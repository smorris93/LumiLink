package com.lumilink.network

import com.lumilink.model.CameraPhoto
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The children of one UPnP container plus the paging counts from the Browse response.
 *
 * @param numberReturned how many direct children this Browse call returned (for paging).
 * @param totalMatches total children the container has (for paging).
 */
data class DidlListing(
    val containerIds: List<String>,
    val photos: List<CameraPhoto>,
    val numberReturned: Int,
    val totalMatches: Int,
)

/**
 * Parses a UPnP ContentDirectory `Browse` response into containers + photos + paging counts.
 *
 * The SOAP response wraps the DIDL-Lite document inside a `<Result>` element as *escaped* text;
 * an XML parser un-escapes it, so we read `<Result>`'s text and parse it as a second document.
 * `NumberReturned`/`TotalMatches` sit alongside `<Result>` and drive paging.
 *
 * Uses only `javax.xml` (DOM), so it stays unit-testable off-device.
 */
object DidlParser {

    fun parse(browseResponseXml: String): DidlListing {
        val outer = parseXml(browseResponseXml)
        val resultNodes = outer.getElementsByTagNameNS("*", "Result")

        val didl: String?
        val number: Int?
        val total: Int?
        if (resultNodes.length > 0) {
            didl = resultNodes.item(0).textContent?.trim()
            number = intField(outer, "NumberReturned")
            total = intField(outer, "TotalMatches")
        } else if (browseResponseXml.contains("DIDL-Lite", ignoreCase = true)) {
            didl = browseResponseXml // caller/test handed us the DIDL document directly
            number = null
            total = null
        } else {
            didl = null
            number = null
            total = null
        }

        if (didl.isNullOrEmpty()) {
            return DidlListing(emptyList(), emptyList(), number ?: 0, total ?: 0)
        }

        val doc = parseXml(didl)
        val containerIds = elementsByLocalName(doc, "container")
            .mapNotNull { it.getAttribute("id").ifBlank { null } }
        val photos = elementsByLocalName(doc, "item").mapNotNull { toPhoto(it) }

        val effectiveNumber = number ?: (containerIds.size + photos.size)
        val effectiveTotal = total ?: effectiveNumber
        return DidlListing(containerIds, photos, effectiveNumber, effectiveTotal)
    }

    /** One `<res>` entry, normalized so we can pick the best thumbnail vs. original. */
    private data class ResEntry(
        val url: String,
        val size: Long,
        val isThumb: Boolean,
        val isRaw: Boolean,
        val isImage: Boolean,
    )

    private fun toPhoto(item: Element): CameraPhoto? {
        val id = item.getAttribute("id").ifBlank { return null }
        val title = firstChildTextByLocalName(item, "title") ?: id

        val resources = buildResources(item)
        if (resources.isEmpty()) return null

        val images = resources.filter { it.isImage }
        // Thumbnail: the tagged thumbnail if present, else the *smallest* image (never the 8 MB
        // original — that was the cause of the slow grid).
        val thumbnailUrl = resources.firstOrNull { it.isThumb }?.url
            ?: images.minByOrNull { it.size }?.url

        val fullJpeg = images.filter { !it.isThumb }.maxByOrNull { it.size }
        val raw = resources.firstOrNull { it.isRaw }

        return when {
            fullJpeg != null ->
                CameraPhoto(id, title, isRaw = false, thumbnailUrl, fullJpeg.url, fullJpeg.size.takeIf { it > 0 })
            raw != null ->
                CameraPhoto(id, title, isRaw = true, thumbnailUrl, raw.url, raw.size.takeIf { it > 0 })
            else -> resources.maxByOrNull { it.size }?.let {
                CameraPhoto(id, title, isRaw = false, thumbnailUrl, it.url, it.size.takeIf { s -> s > 0 })
            }
        }
    }

    private fun buildResources(item: Element): List<ResEntry> {
        val out = mutableListOf<ResEntry>()
        val resList = item.getElementsByTagNameNS("*", "res")
        for (i in 0 until resList.length) {
            val res = resList.item(i) as Element
            val url = res.textContent?.trim().orEmpty()
            if (url.isEmpty()) continue
            val protocolInfo = res.getAttribute("protocolInfo").lowercase()
            val lowerUrl = url.lowercase()
            val size = res.getAttribute("size").toLongOrNull()
                ?: resolutionArea(res.getAttribute("resolution"))
            val isThumb = protocolInfo.contains("_tn") || lowerUrl.contains("/dt")
            val isRaw = protocolInfo.contains("raw") || lowerUrl.endsWith(".rw2")
            val isImage = !isRaw &&
                (protocolInfo.contains("image/") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg"))
            out += ResEntry(url, size, isThumb, isRaw, isImage)
        }
        return out
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

    private fun intField(doc: Document, local: String): Int? {
        val nodes = doc.getElementsByTagNameNS("*", local)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim()?.toIntOrNull() else null
    }

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }
}
