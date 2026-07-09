package com.lumilink.model

/**
 * The camera-connection state the UI reacts to.
 *
 * Kotlin note: a `sealed interface` is a closed set of subtypes — all implementations are in
 * this file. That lets a `when (state)` be checked for exhaustiveness by the compiler (no
 * `else` branch needed), similar to a Java sealed class but more concise. `data object` is a
 * singleton with a nice `toString()`; `data class` carries values.
 */
sealed interface ConnectionState {

    /** No camera saved yet — the user must enter the hotspot SSID + password. */
    data object NeedsSetup : ConnectionState

    /** A camera is saved but we are not connected. */
    data object Idle : ConnectionState

    /** requestNetwork is in flight — may include the one-time system approval dialog. */
    data object Connecting : ConnectionState

    /** Connected; every app socket is now bound to the camera network. */
    data class Connected(val ssid: String) : ConnectionState

    /** The attempt failed, was declined, or the link dropped. */
    data class Failed(val reason: String) : ConnectionState
}
