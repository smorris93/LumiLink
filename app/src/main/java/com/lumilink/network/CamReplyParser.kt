package com.lumilink.network

/**
 * Parses the two reply formats the camera uses for cam.cgi:
 *  - XML control replies: `<camrply><result>ok</result></camrply>`
 *  - CSV access-control replies: `ok,GX80-218C63,remote,open` or `err_user_refused,GX80-...,...`
 *
 * Returns the status token ("ok", "err_user_refused", …), or "unknown" if neither form matches.
 * Pure and Android-free, so it's unit-testable on the JVM.
 */
object CamReplyParser {

    private val resultRegex = Regex("<result>(.*?)</result>", RegexOption.IGNORE_CASE)

    fun resultOf(reply: String): String {
        val trimmed = reply.trim()

        // XML form first.
        resultRegex.find(trimmed)?.groupValues?.get(1)?.trim()?.let { return it }

        // XML without a <result> element — malformed for our purposes.
        if (trimmed.startsWith("<")) return "unknown"

        // CSV form: the status is the first comma/newline-delimited field.
        val firstField = trimmed.substringBefore(',').substringBefore('\n').trim()
        return firstField.ifEmpty { "unknown" }
    }
}
