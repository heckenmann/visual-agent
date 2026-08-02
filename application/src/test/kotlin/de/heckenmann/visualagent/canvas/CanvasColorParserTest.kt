package de.heckenmann.visualagent.canvas

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CanvasColorParserTest {
    @Test
    fun `parseHexColor handles rgb with hash`() {
        assertEquals(0xFF112233.toInt(), parseHexColor("#112233"))
    }

    @Test
    fun `parseHexColor handles rgb without hash`() {
        assertEquals(0xFF112233.toInt(), parseHexColor("112233"))
    }

    @Test
    fun `parseHexColor handles argb with hash`() {
        assertEquals(0x88112233.toInt(), parseHexColor("#88112233"))
    }

    @Test
    fun `parseHexColor returns null for empty input`() {
        assertNull(parseHexColor(""))
    }

    @Test
    fun `parseHexColor returns null for invalid length`() {
        assertNull(parseHexColor("#123"))
    }

    @Test
    fun `parseHexColor returns null for non-hex input`() {
        assertNull(parseHexColor("#GGHHII"))
    }
}
