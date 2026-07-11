package com.lumilink.network

import com.lumilink.model.CameraStatus

/**
 * Parses the camera's `mode=getstate` XML reply into a [CameraStatus].
 *
 * A GX80 reply looks like:
 * ```
 * <camrply><result>ok</result><state>
 *   <batt>3/3</batt><cammode>rec</cammode><sdcardstatus>write_enable</sdcardstatus>
 *   <sd_memory>set</sd_memory><rec>off</rec>...
 * </state></camrply>
 * ```
 * We pull only the fields we surface, tolerantly: any missing element just stays null. Pure and
 * Android-free so it's unit-testable on the JVM (like [CamReplyParser] and [DidlParser]).
 */
object CameraStatusParser {

    // Firmware varies the tag names slightly, so accept the known aliases for each field.
    private val battery = tagRegex("batt", "battery", "battlevel")
    private val mode = tagRegex("cammode", "mode")
    private val rec = tagRegex("rec", "video")
    private val sdCard = tagRegex("sdcardstatus", "sd_memory", "sdcard")

    fun parse(reply: String): CameraStatus {
        val xml = reply.trim()
        return CameraStatus(
            battery = firstMatch(battery, xml),
            mode = firstMatch(mode, xml)?.lowercase(),
            recording = firstMatch(rec, xml)?.let { it.equals("on", ignoreCase = true) } ?: false,
            sdCard = firstMatch(sdCard, xml),
        )
    }

    private fun firstMatch(regex: Regex, xml: String): String? =
        regex.find(xml)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

    /** Matches `<tag>value</tag>` for any of the given tag names, case-insensitively. */
    private fun tagRegex(vararg tags: String): Regex =
        Regex("<(?:${tags.joinToString("|")})>(.*?)</(?:${tags.joinToString("|")})>", RegexOption.IGNORE_CASE)
}
