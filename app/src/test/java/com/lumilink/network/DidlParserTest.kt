package com.lumilink.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the DIDL-Lite parser using a realistic ContentDirectory `Browse` response
 * (DIDL escaped inside `<Result>`, as UPnP requires).
 */
class DidlParserTest {

    private val browseResponse = """
        <?xml version="1.0"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
         <s:Body>
          <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
           <Result>&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;
             &lt;container id="1" parentID="0" restricted="1"&gt;&lt;dc:title&gt;100_PANA&lt;/dc:title&gt;&lt;/container&gt;
             &lt;item id="1030413" parentID="1" restricted="1"&gt;
               &lt;dc:title&gt;P1030413&lt;/dc:title&gt;
               &lt;upnp:class&gt;object.item.imageItem.photo&lt;/upnp:class&gt;
               &lt;res protocolInfo="http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN" size="12000"&gt;http://192.168.54.1:50001/DT1030413.jpg&lt;/res&gt;
               &lt;res protocolInfo="http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG" size="8388608" resolution="4592x3448"&gt;http://192.168.54.1:50001/DO1030413.jpg&lt;/res&gt;
             &lt;/item&gt;
             &lt;item id="1030414" parentID="1" restricted="1"&gt;
               &lt;dc:title&gt;P1030414&lt;/dc:title&gt;
               &lt;res protocolInfo="http-get:*:image/x-raw:*" size="24000000"&gt;http://192.168.54.1:50001/DO1030414.rw2&lt;/res&gt;
             &lt;/item&gt;
           &lt;/DIDL-Lite&gt;</Result>
           <NumberReturned>3</NumberReturned>
           <TotalMatches>3</TotalMatches>
          </u:BrowseResponse>
         </s:Body>
        </s:Envelope>
    """.trimIndent()

    @Test
    fun `extracts container ids`() {
        val listing = DidlParser.parse(browseResponse)
        assertEquals(listOf("1"), listing.containerIds)
    }

    @Test
    fun `parses a jpeg photo with thumbnail and original`() {
        val jpeg = DidlParser.parse(browseResponse).photos.first { it.id == "1030413" }
        assertEquals("P1030413", jpeg.title)
        assertFalse(jpeg.isRaw)
        assertEquals("http://192.168.54.1:50001/DT1030413.jpg", jpeg.thumbnailUrl)
        assertEquals("http://192.168.54.1:50001/DO1030413.jpg", jpeg.originalUrl)
        assertEquals(8_388_608L, jpeg.sizeBytes)
    }

    @Test
    fun `parses a raw-only photo`() {
        val raw = DidlParser.parse(browseResponse).photos.first { it.id == "1030414" }
        assertTrue(raw.isRaw)
        assertEquals("http://192.168.54.1:50001/DO1030414.rw2", raw.originalUrl)
        assertNull(raw.thumbnailUrl)
    }

    @Test
    fun `empty result yields nothing`() {
        val empty = "<BrowseResponse><Result></Result></BrowseResponse>"
        val listing = DidlParser.parse(empty)
        assertTrue(listing.photos.isEmpty())
        assertTrue(listing.containerIds.isEmpty())
    }
}
