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

    @Test
    fun `reads ok from csv access-control reply`() {
        assertEquals("ok", CamReplyParser.resultOf("ok,GX80-218C63,remote,open"))
    }

    @Test
    fun `reads error token from csv access-control reply`() {
        assertEquals(
            "err_user_refused",
            CamReplyParser.resultOf("err_user_refused,GX80-218C63,remote,open"),
        )
    }
}
