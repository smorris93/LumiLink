package com.lumilink.network

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads a UPnP device-description XML and returns the absolute control URL of the camera's
 * ContentDirectory service (the endpoint we POST `Browse` requests to).
 *
 * Pure `javax.xml`, so it's unit-testable off-device.
 */
object DeviceDescriptionParser {

    /**
     * @param descriptionXml the fetched device-description document.
     * @param descriptionUrl the URL it was fetched from (used to resolve relative control URLs).
     */
    fun contentDirectoryControlUrl(descriptionXml: String, descriptionUrl: String): String? {
        val doc = parseXml(descriptionXml)
        val base = urlBase(doc) ?: baseFromUrl(descriptionUrl)

        val services = doc.getElementsByTagNameNS("*", "service")
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val type = childText(service, "serviceType") ?: continue
            if (type.contains("ContentDirectory", ignoreCase = true)) {
                val controlUrl = childText(service, "controlURL") ?: continue
                return resolve(base, controlUrl)
            }
        }
        return null
    }

    private fun urlBase(doc: Document): String? {
        val nodes = doc.getElementsByTagNameNS("*", "URLBase")
        return if (nodes.length > 0) nodes.item(0).textContent?.trim()?.trimEnd('/') else null
    }

    private fun baseFromUrl(url: String): String {
        val parsed = URL(url)
        val port = if (parsed.port != -1) ":${parsed.port}" else ""
        return "${parsed.protocol}://${parsed.host}$port"
    }

    /** Turns a possibly-relative control URL into an absolute one. */
    private fun resolve(base: String, path: String): String = when {
        path.startsWith("http", ignoreCase = true) -> path
        path.startsWith("/") -> base + path
        else -> "$base/$path"
    }

    private fun childText(parent: Element, local: String): String? {
        val nodes = parent.getElementsByTagNameNS("*", local)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }
}
