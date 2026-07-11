package com.lumilink.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the getstate reply parser (no Android, no device needed). */
class CameraStatusParserTest {

    @Test
    fun `parses a full GX80 getstate reply`() {
        val xml = "<?xml version=\"1.0\"?><camrply><result>ok</result><state>" +
            "<batt>3/3</batt><cammode>rec</cammode><sdcardstatus>write_enable</sdcardstatus>" +
            "<sd_memory>set</sd_memory><rec>off</rec></state></camrply>"
        val status = CameraStatusParser.parse(xml)
        assertEquals("3/3", status.battery)
        assertEquals("rec", status.mode)
        assertEquals("write_enable", status.sdCard)
        assertFalse(status.recording)
    }

    @Test
    fun `lowercases mode and detects active recording`() {
        val xml = "<state><cammode>REC</cammode><rec>on</rec></state>"
        val status = CameraStatusParser.parse(xml)
        assertEquals("rec", status.mode)
        assertTrue(status.recording)
    }

    @Test
    fun `tolerates missing fields`() {
        val status = CameraStatusParser.parse("<camrply><result>ok</result></camrply>")
        assertNull(status.battery)
        assertNull(status.mode)
        assertNull(status.sdCard)
        assertFalse(status.recording)
    }

    @Test
    fun `accepts alternate battery tag`() {
        assertEquals("battery3", CameraStatusParser.parse("<state><battery>battery3</battery></state>").battery)
    }
}
