package com.lumilink.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Finds the camera's UPnP media server via SSDP (the UPnP discovery protocol): we multicast an
 * `M-SEARCH` and read back the responder's `LOCATION` header, which points at its device
 * description XML.
 *
 * Because the app's sockets are bound to the camera network, the only responder is the camera.
 */
class SsdpDiscovery {

    /** Returns the device-description URL (the SSDP `LOCATION`), or null if nothing responded. */
    suspend fun discoverMediaServerDescriptionUrl(): String? = withContext(Dispatchers.IO) {
        val searchMessage = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $MULTICAST_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: urn:schemas-upnp-org:device:MediaServer:1\r\n")
            append("\r\n")
        }.toByteArray()

        val group = InetAddress.getByName(MULTICAST_ADDRESS)
        DatagramSocket().use { socket ->
            socket.soTimeout = RESPONSE_TIMEOUT_MS
            // Send a couple of probes — UDP is lossy and the first can be dropped during handoff.
            repeat(SEARCH_ATTEMPTS) {
                socket.send(DatagramPacket(searchMessage, searchMessage.size, group, SSDP_PORT))
            }

            val buffer = ByteArray(BUFFER_SIZE)
            val deadline = System.nanoTime() + DISCOVERY_WINDOW_MS * 1_000_000L
            while (System.nanoTime() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    break // no more responses within the window
                }
                val response = String(packet.data, 0, packet.length)
                val location = headerValue(response, "LOCATION")
                if (location != null) {
                    Log.i(TAG, "Discovered media server at $location")
                    return@withContext location
                }
            }
        }
        Log.w(TAG, "No SSDP response — camera media server not found")
        null
    }

    /** Reads a header value out of a raw HTTP-style SSDP response (case-insensitive name). */
    private fun headerValue(response: String, header: String): String? =
        response.lineSequence()
            .firstOrNull { it.startsWith("$header:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()

    private companion object {
        const val TAG = "SsdpDiscovery"
        const val MULTICAST_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val RESPONSE_TIMEOUT_MS = 1500
        const val DISCOVERY_WINDOW_MS = 4000L
        const val SEARCH_ATTEMPTS = 2
        const val BUFFER_SIZE = 4096
    }
}
