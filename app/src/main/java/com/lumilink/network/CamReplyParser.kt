package com.lumilink.network

/**
 * Parses the tiny XML replies the camera returns to cam.cgi commands, e.g.
 * `<camrply><result>ok</result></camrply>`.
 *
 * Kotlin note: `object` is a singleton — a class with exactly one instance, accessed by name
 * (`CamReplyParser.resultOf(...)`), like a holder of stateless helper functions. It's a pure
 * function with no Android dependencies, so it's unit-testable on the plain JVM.
 */
object CamReplyParser {

    private val resultRegex = Regex("<result>(.*?)</result>", RegexOption.IGNORE_CASE)

    /** Returns the `<result>` text (e.g. "ok"), or "unknown" if the tag is absent. */
    fun resultOf(xml: String): String =
        resultRegex.find(xml)?.groupValues?.get(1)?.trim() ?: "unknown"
}
