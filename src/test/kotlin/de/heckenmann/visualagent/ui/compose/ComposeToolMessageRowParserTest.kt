package de.heckenmann.visualagent.ui.compose

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComposeToolMessageRowParserTest {
    @Test
    fun `parseToolMetadata extracts all fields`() {
        val metadata =
            buildJsonObject {
                put("toolId", "file:read")
                put("status", "ok")
                put("durationMillis", 42L)
                put("inputJson", "{\"path\":\"/tmp/hi.txt\"}")
                put("resultContent", "hello")
                put("resultError", "")
            }.toString()

        val parsed = parseToolMetadata(metadata)

        assertEquals("file:read", parsed.toolId)
        assertEquals("ok", parsed.status)
        assertEquals(42L, parsed.durationMillis)
        assertEquals("{\"path\":\"/tmp/hi.txt\"}", parsed.inputJson)
        assertEquals("hello", parsed.resultContent)
        assertEquals("", parsed.resultError)
    }

    @Test
    fun `parseToolMetadata uses defaults for empty input`() {
        val parsed = parseToolMetadata(null)

        assertEquals("tool", parsed.toolId)
        assertEquals("ok", parsed.status)
        assertNull(parsed.durationMillis)
        assertNull(parsed.inputJson)
        assertNull(parsed.resultContent)
        assertNull(parsed.resultError)
    }

    @Test
    fun `parseToolMetadata uses defaults for invalid json`() {
        val parsed = parseToolMetadata("not-json")

        assertEquals("tool", parsed.toolId)
        assertEquals("ok", parsed.status)
    }
}
