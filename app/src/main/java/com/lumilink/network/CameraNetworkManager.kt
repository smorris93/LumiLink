package com.lumilink.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.util.Log
import com.lumilink.model.CameraCredentials
import com.lumilink.model.ConnectionState
import com.lumilink.service.CameraConnectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the connection to the camera's Wi-Fi hotspot — the heart of the app and the reason it
 * exists.
 *
 * The problem: the camera hotspot has no internet, so modern Android / Xiaomi HyperOS route
 * traffic over cellular instead and aggressively drop the "useless" Wi-Fi. The fix, all here:
 *   1. request the network with the INTERNET capability **removed**, so the system will grant it;
 *   2. in `onAvailable`, **bind the whole process** to that network so every socket uses it;
 *   3. **keep the request registered** for the whole session, which stops the system reaping it.
 *
 * Kotlin/Android note: `ConnectivityManager.NetworkCallback` fires on a system background thread.
 * We publish state through a `StateFlow` (thread-safe, observable) rather than touching UI directly.
 */
class CameraNetworkManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)

    // `_state` is the private mutable source; `state` is the read-only view the UI collects.
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // Held for the whole session. Unregistering it is what releases the camera network.
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Connect to the given camera hotspot. The first time for a given access point Android shows
     * a one-time system "connect to this Wi-Fi?" dialog; after the user accepts it's cached and
     * connects silently thereafter.
     */
    fun connect(credentials: CameraCredentials) {
        // Tear down any previous request first so a retry after a dropped connection always works
        // (a stale callback used to block reconnection).
        networkCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        networkCallback = null
        _state.value = ConnectionState.Connecting
        // Give the process foreground priority so it isn't killed during long transfers.
        CameraConnectionService.start(appContext)

        val specifierBuilder = WifiNetworkSpecifier.Builder()
            .setSsid(credentials.ssid)
        // The GX80 hotspot may be open (no password) if its Wi-Fi password setting is off.
        // Only set a WPA2 passphrase when the user actually provided one.
        if (credentials.passphrase.isNotBlank()) {
            specifierBuilder.setWpa2Passphrase(credentials.passphrase)
        }
        val specifier = specifierBuilder.build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // Critical: the camera AP has no internet. Without removing this capability the
            // request would never be satisfied.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Route ALL of this process's sockets over the camera network from now on.
                val bound = connectivityManager.bindProcessToNetwork(network)
                Log.i(TAG, "Camera network available; process bound = $bound")
                _state.value = ConnectionState.Connected(credentials.ssid)
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Camera network lost")
                _state.value = ConnectionState.Failed("Connection to the camera was lost")
            }

            override fun onUnavailable() {
                Log.w(TAG, "Camera network unavailable (declined or not found)")
                _state.value =
                    ConnectionState.Failed("Couldn't connect — is the camera's Wi-Fi turned on?")
            }
        }
        networkCallback = callback
        connectivityManager.requestNetwork(request, callback)
    }

    /** Disconnect and let the system return to normal networking. */
    fun disconnect() {
        connectivityManager.bindProcessToNetwork(null)
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        networkCallback = null
        CameraConnectionService.stop(appContext)
        _state.value = ConnectionState.Idle
    }

    private companion object {
        const val TAG = "CameraNetworkManager"
    }
}
