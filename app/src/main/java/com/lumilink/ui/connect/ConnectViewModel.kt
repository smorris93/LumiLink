package com.lumilink.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumilink.di.AppContainer
import com.lumilink.data.CameraCredentialsStore
import com.lumilink.model.CameraCredentials
import com.lumilink.model.ConnectionState
import com.lumilink.network.CameraNetworkManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the Connect screen's state and actions. The UI observes [connectionState] and
 * [savedCredentials]; it never touches the network layer directly.
 *
 * Kotlin/Android note: a `ViewModel` survives configuration changes (e.g. screen rotation).
 * `viewModelScope` is a coroutine scope automatically cancelled when the ViewModel is cleared.
 */
class ConnectViewModel(
    private val networkManager: CameraNetworkManager,
    private val credentialsStore: CameraCredentialsStore,
) : ViewModel() {

    /** Live connection state from the network manager, surfaced straight to the UI. */
    val connectionState: StateFlow<ConnectionState> = networkManager.state

    /**
     * The last saved camera, or null. `stateIn` converts the store's cold Flow into a hot
     * StateFlow with an always-available current value for the UI to prefill fields.
     */
    val savedCredentials: StateFlow<CameraCredentials?> = credentialsStore.credentials.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /** Save the entered credentials and start connecting. */
    fun connect(ssid: String, passphrase: String) {
        val credentials = CameraCredentials(ssid.trim(), passphrase)
        viewModelScope.launch { credentialsStore.save(credentials) }
        networkManager.connect(credentials)
    }

    fun disconnect() {
        networkManager.disconnect()
    }

    companion object {
        /**
         * Builds this ViewModel with its dependencies pulled from the DI container.
         * `viewModelFactory { initializer { ... } }` is the idiomatic way to construct a
         * ViewModel that takes constructor arguments.
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConnectViewModel(container.cameraNetworkManager, container.credentialsStore)
            }
        }
    }
}
