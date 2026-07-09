package com.lumilink.network

import com.lumilink.model.CameraPhoto
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** The children of one UPnP container: sub-container ids to recurse into, plus photo items. */
data class DidlListing(
    val containerIds: List<String>,
    val photos: List<CameraPhoto>,
)

/**
 * Parses a UPnP ContentDirectory `Browse` response into containers + photos.
 *
 * The SOAP response wraps the DIDL-Lite document inside a `<Result>` element as *escaped* text
 * (`&lt;DIDL-Lite&gt;...`). An XML parser un-escapes that automatically, so we read `<Result>`'s
 * text content and parse it as a second XML document.
 *
 * Uses only `javax.xml` (DOM), which exists on both Android and the plain JVM, so this stays
 * unit-testable without a device. Namespace-agnostic lookups (`getElementsByTagNameNS("*", ...)`)
 * avoid fighting the many DIDL namespaces (dc:, upnp:, …).
 */
object DidlParser {

    fun parse(browseResponseXml: String): DidlListing {
        val didl = extractResultDidl(browseResponseXml) ?: return DidlListing(emptyList(), emptyList())
        val doc = parseXml(didl)

        val containerIds = elementsByLocalName(doc, "container")
            .mapNotNull { it.getAttribute("id").ifBlank { null } }
        val photos = elementsByLocalName(doc, "item").mapNotNull { toPhoto(it) }
        return DidlListing(containerIds, photos)
    }

    /** Pulls the (un-escaped) DIDL-Lite string out of the SOAP `<Result>`, tolerating raw DIDL. */
    private fun extractResultDidl(xml: String): String? {
        val doc = parseXml(xml)
        val results = doc.getElementsByTagNameNS("*", "Result")
        if (results.length == 0) {
            // Some callers/tests may hand us the DIDL document directly.
            return if (xml.contains("DIDL-Lite", ignoreCase = true)) xml else null
        }
        // An empty <Result/> means "no children" — return null rather than parse an empty string.
        val text = results.item(0).textContent?.trim()
        return if (text.isNullOrEmpty()) null else text
    }

    private fun toPhoto(item: Element): CameraPhoto? {
        val id = item.getAttribute("id").ifBlank { return null }
        val title = firstChildTextByLocalName(item, "title") ?: id

        var thumbnailUrl: String? = null
        var jpegUrl: String? = null
        var jpegSize: Long? = null
        var jpegScore = -1L
        var rawUrl: String? = null
        var rawSize: Long? = null

        val resList = item.getElementsByTagNameNS("*", "res")
        for (i in 0 until resList.length) {
            val res = resList.item(i) as Element
            val url = res.textContent?.trim().orEmpty()
            if (url.isEmpty()) continue
            val protocolInfo = res.getAttribute("protocolInfo").lowercase()
            val size = res.getAttribute("size").toLongOrNull()
            val lowerUrl = url.lowercase()
            // Prefer real byte size to rank "largest"; fall back to pixel area, else 0.
            val score = size ?: resolutionArea(res.getAttribute("resolution"))

            when {
                protocolInfo.contains("_tn") || lowerUrl.contains("/dt") ->
                    if (thumbnailUrl == null) thumbnailUrl = url
                protocolInfo.contains("raw") || lowerUrl.endsWith(".rw2") -> {
                    rawUrl = url
                    rawSize = size
                }
                protocolInfo.contains("image/jpeg") || lowerUrl.endsWith(".jpg") ->
                    if (score >= jpegScore) {
                        jpegScore = score
                        jpegUrl = url
                        jpegSize = size
                    }
            }
        }

        // If no dedicated thumbnail was advertised, reuse the full JPEG (heavier, still works).
        if (thumbnailUrl == null) thumbnailUrl = jpegUrl

        return when {
            jpegUrl != null -> CameraPhoto(id, title, isRaw = false, thumbnailUrl, jpegUrl, jpegSize)
            rawUrl != null -> CameraPhoto(id, title, isRaw = true, thumbnailUrl, rawUrl, rawSize)
            else -> null // an item with no downloadable image resource — skip it
        }
    }

    private fun resolutionArea(resolution: String): Long {
        val parts = resolution.split("x", "X")
        if (parts.size != 2) return 0
        val width = parts[0].trim().toLongOrNull() ?: return 0
        val height = parts[1].trim().toLongOrNull() ?: return 0
        return width * height
    }

    private fun firstChildTextByLocalName(parent: Element, local: String): String? {
        val nodes = parent.getElementsByTagNameNS("*", local)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }

    private fun elementsByLocalName(doc: Document, local: String): List<Element> {
        val nodes = doc.getElementsByTagNameNS("*", local)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }
}
