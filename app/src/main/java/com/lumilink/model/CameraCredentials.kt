package com.lumilink.model

/**
 * The saved camera hotspot login.
 *
 * Kotlin note: `data class` auto-generates `equals()`, `hashCode()`, `toString()`, and `copy()`
 * from the constructor properties — no boilerplate, unlike a plain Java class.
 */
data class CameraCredentials(
    val ssid: String,
    val passphrase: String,
)
