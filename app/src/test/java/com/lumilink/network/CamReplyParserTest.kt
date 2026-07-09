package com.lumilink.network

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-JVM tests for the camera XML reply parser (no Android, no device needed). */
class CamReplyParserTest {

    @Test
    fun `reads ok result`() {
        val xml = "<?xml version=\"1.0\"?><camrply><result>ok</result></camrply>"
        assertEquals("ok", CamReplyParser.resultOf(xml))
    }

    @Test
    fun `reads failure result`() {
        val xml = "<camrply><result>err_param</result></camrply>"
        assertEquals("err_param", CamReplyParser.resultOf(xml))
    }

    @Test
    fun `returns unknown when result tag missing`() {
        assertEquals("unknown", CamReplyParser.resultOf("<camrply></camrply>"))
    }
}
