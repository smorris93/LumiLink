package com.lumilink.network

import com.lumilink.model.CameraExposure

/**
 * Parses the camera's `getinfo&type=curmenu` XML for the current exposure settings.
 *
 * Relevant items look like `<item id="menu_item_id_sensitivity" enable="yes" value="3200" />`.
 * The GX80 reports ISO here; aperture/shutter are absent in Manual mode (dial-controlled), so those
 * stay null. Pure and Android-free, so it's unit-testable on the JVM.
 */
object CurMenuParser {

    /** ISO is raw ("3200"); "auto"/"iso_auto" mean auto ISO. */
    private val iso = itemValue("sensitivity")
    private val aperture = itemValue("aperture", "diaphragm", "fnumber")
    private val shutter = itemValue("shutter_speed", "shtrspeed")
    private val exposureComp = itemValue("exposure", "exposure3")

    fun parse(xml: String): CameraExposure = CameraExposure(
        iso = normalizeIso(iso.find(xml)?.groupValues?.get(1)),
        aperture = aperture.find(xml)?.groupValues?.get(1),
        shutter = shutter.find(xml)?.groupValues?.get(1),
        exposureComp = exposureComp.find(xml)?.groupValues?.get(1),
    )

    private fun normalizeIso(raw: String?): String? = when {
        raw == null -> null
        raw.contains("auto", ignoreCase = true) -> "AUTO"
        else -> raw
    }

    /** Matches `<item id="menu_item_id_<name>" ... value="X">` for any of the given item names. */
    private fun itemValue(vararg names: String): Regex {
        val alt = names.joinToString("|") { "menu_item_id_$it" }
        return Regex("<item id=\"(?:$alt)\"[^>]*\\bvalue=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    }
}
