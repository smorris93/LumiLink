package com.lumilink.model

/**
 * A snapshot of the camera's state as reported by `cam.cgi?mode=getstate`.
 *
 * The GX80's getstate reply covers hardware/mode status but NOT exposure (ISO/aperture/shutter/EV) —
 * those are pushed in the live-view stream header, so [Exposure] stays a separate, later concern.
 * Every field is nullable/optional because the reply's shape varies by firmware and mode; the UI
 * shows "—" for anything unknown rather than guessing.
 */
data class CameraStatus(
    /** Raw battery token, e.g. "3/3" or "battery3". Null if absent. */
    val battery: String? = null,
    /** "rec" or "play" (record vs playback), or null if the camera didn't report it. */
    val mode: String? = null,
    /** True while the camera is actively recording video. */
    val recording: Boolean = false,
    /** SD-card status token, e.g. "set" / "write_enable" / "no_card". */
    val sdCard: String? = null,
) {
    companion object {
        /** An all-unknown status, shown before the first successful poll. */
        val UNKNOWN = CameraStatus()
    }
}
