package com.lumilink.ui.control

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumilink.di.AppContainer
import com.lumilink.model.CameraStatus
import com.lumilink.network.CameraClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the Control screen (MVP2): puts the camera in record mode, polls its state, and issues
 * shutter / autofocus / video-record commands. Live view and exposure numbers arrive in MVP3 with
 * the MJPEG stream, so the exposure grid stays a placeholder for now.
 *
 * Polling is lifecycle-driven: the screen calls [start] when it appears and [stop] when it leaves,
 * so we don't hammer the camera while the user is on another tab.
 */
class ControlViewModel(
    private val cameraClient: CameraClient,
) : ViewModel() {

    data class UiState(
        /** True once the camera has acknowledged record mode; controls are enabled only then. */
        val recordModeReady: Boolean = false,
        val status: CameraStatus = CameraStatus.UNKNOWN,
        val busy: Boolean = false,
        /** Transient one-shot text for a snackbar (capture confirmation, errors). */
        val message: String? = null,
    ) {
        val videoRecording: Boolean get() = status.recording
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    /** Called when the Control screen becomes visible. Enters record mode, then polls state. */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            try {
                // The camera only accepts commands after the access-control handshake; pair() is
                // idempotent (a prior session returns already-connected), so it's safe every time.
                cameraClient.pair()
                cameraClient.enterRecordMode()
                _uiState.update { it.copy(recordModeReady = true) }
            } catch (e: Exception) {
                // The camera may be asleep or still in playback; surface it but keep polling so a
                // recovered camera heals the screen without a manual retry.
                Log.w(TAG, "enterRecordMode failed: ${e.message}")
                _uiState.update {
                    it.copy(message = "Couldn't switch the camera to record mode — wake it and try again.")
                }
            }
            while (isActive) {
                try {
                    val status = cameraClient.fetchStatus()
                    _uiState.update {
                        // A successful poll implies record mode is live even if the earlier switch errored.
                        it.copy(status = status, recordModeReady = it.recordModeReady || status.mode == "rec")
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "status poll failed: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Called when the Control screen leaves the composition (tab switch / disconnect). */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun capture() = runCommand("Captured") { cameraClient.capture() }

    fun autofocus() = runCommand(
        successMessage = "Focused",
        // The GX80 rejects standalone remote AF (err_reject) unless live view is streaming, or when
        // the lens is in manual focus. Explain rather than show a raw error; the shutter AFs anyway.
        errorMapper = { raw ->
            if (raw.contains("err_reject", ignoreCase = true)) {
                "Autofocus needs live view running (arrives in MVP3) or the lens set to AF. " +
                    "The shutter still autofocuses before each shot."
            } else {
                null
            }
        },
    ) { cameraClient.autofocus() }

    fun toggleVideoRecord() {
        if (_uiState.value.videoRecording) {
            runCommand("Recording stopped") { cameraClient.stopVideoRecord() }
        } else {
            runCommand("Recording started") { cameraClient.startVideoRecord() }
        }
    }

    /**
     * Runs a one-shot camera command with a busy guard and a success/error snackbar message.
     * [errorMapper] can turn a raw failure message into friendlier text (return null to keep raw).
     */
    private fun runCommand(
        successMessage: String,
        errorMapper: (String) -> String? = { null },
        command: suspend () -> Unit,
    ) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val message = try {
                command()
                successMessage
            } catch (e: Exception) {
                Log.w(TAG, "command failed: ${e.message}")
                val raw = e.message ?: "Camera command failed"
                errorMapper(raw) ?: raw
            }
            _uiState.update { it.copy(busy = false, message = message) }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        stop()
    }

    companion object {
        private const val TAG = "ControlViewModel"
        private const val POLL_INTERVAL_MS = 2_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ControlViewModel(container.cameraClient)
            }
        }
    }
}
