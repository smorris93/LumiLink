package com.lumilink.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-JVM tests for parsing exposure settings out of the curmenu reply. */
class CurMenuParserTest {

    @Test
    fun `reads ISO from the sensitivity item`() {
        val xml = """<item id="menu_item_id_sensitivity" enable="yes" value="3200" />"""
        assertEquals("3200", CurMenuParser.parse(xml).iso)
    }

    @Test
    fun `maps auto ISO to AUTO`() {
        val xml = """<item id="menu_item_id_sensitivity" enable="yes" value="iso_auto" />"""
        assertEquals("AUTO", CurMenuParser.parse(xml).iso)
    }

    @Test
    fun `aperture and shutter are null when the GX80 omits them (Manual mode)`() {
        val xml = """<item id="menu_item_id_shutter_speed" enable="no" />
            <item id="menu_item_id_sensitivity" enable="yes" value="400" />"""
        val exposure = CurMenuParser.parse(xml)
        assertEquals("400", exposure.iso)
        assertNull(exposure.aperture)
        assertNull(exposure.shutter)
    }

    @Test
    fun `does not confuse the sensitivity_db item for ISO`() {
        // The real reply lists sensitivity first, then sensitivity_db; ensure we match the former.
        val xml = """<item id="menu_item_id_sensitivity" enable="yes" value="1600" />""" +
            """<item id="menu_item_id_sensitivity_db" enable="no" value="0" />"""
        assertEquals("1600", CurMenuParser.parse(xml).iso)
    }
}
