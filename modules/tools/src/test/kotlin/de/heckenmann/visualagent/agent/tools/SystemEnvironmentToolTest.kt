package de.heckenmann.visualagent.agent.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemEnvironmentToolTest {
    private val values =
        mapOf(
            "API_TOKEN" to "sensitive-marker-123456",
            "PATH" to "/usr/bin",
            "UNICODE_VALUE" to "A😀B",
            "VISUAL_AGENT_MODE" to "test",
        )
    private val tool = SystemEnvironmentTool(EnvironmentVariablesProvider { values })

    @Test
    fun `list returns bounded values including secrets without redaction`() {
        val result = tool.execute("""{"action":"list","pageSize":1,"valueLimit":8}""", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("sensitiv"))
        assertTrue(result.content.contains("\"redacted\":false"))
        assertTrue(result.content.contains("\"hasMoreValue\":true"))
        assertTrue(result.content.contains("\"hasMore\":true"))
    }

    @Test
    fun `search matches variable names and get reads a long value in explicit chunks`() {
        val search = tool.execute("""{"action":"search","query":"token"}""", emptyMap())
        val chunk = tool.execute("""{"action":"get","name":"API_TOKEN","valueOffset":10,"valueLimit":8}""", emptyMap())

        assertTrue(search.success)
        assertTrue(search.content.contains("API_TOKEN"))
        assertFalse(search.content.contains("PATH"))
        assertTrue(chunk.success)
        assertTrue(chunk.content.contains("\"value\":\"marker-1\""))
        assertTrue(chunk.content.contains("\"valueLength\":23"))
        assertTrue(chunk.content.contains("\"valueOffset\":10"))
        assertTrue(chunk.content.contains("\"hasMoreValue\":true"))
    }

    @Test
    fun `get distinguishes missing variables and validates offsets`() {
        assertFalse(tool.execute("""{"action":"get","name":"MISSING"}""", emptyMap()).success)
        assertFalse(tool.execute("""{"action":"get","name":"PATH","valueOffset":99}""", emptyMap()).success)
        assertFalse(tool.execute("""{"action":"list","pageSize":11}""", emptyMap()).success)
    }

    @Test
    fun `get chunks environment values by Unicode code point`() {
        val first = tool.execute("""{"action":"get","name":"UNICODE_VALUE","valueLimit":2}""", emptyMap())
        val second = tool.execute("""{"action":"get","name":"UNICODE_VALUE","valueOffset":2,"valueLimit":2}""", emptyMap())

        assertTrue(first.success)
        assertTrue(first.content.contains("\"value\":\"A😀\""))
        assertTrue(first.content.contains("\"nextValueOffset\":2"))
        assertTrue(second.success)
        assertTrue(second.content.contains("\"value\":\"B\""))
        assertFalse(second.content.contains("\"hasMoreValue\":true"))
    }

    @Test
    fun `search requires a query and list supports an empty environment`() {
        assertFalse(tool.execute("""{"action":"search"}""", emptyMap()).success)

        val empty = SystemEnvironmentTool(EnvironmentVariablesProvider { emptyMap() }).execute("{}", emptyMap())

        assertTrue(empty.success)
        assertTrue(empty.content.contains("\"totalVariables\":0"))
        assertEquals("system:env", empty.toolId)
    }

    @Test
    fun `rejects oversized environment queries before reading values`() {
        var reads = 0
        val boundedTool =
            SystemEnvironmentTool(
                EnvironmentVariablesProvider {
                    reads++
                    values
                },
            )

        assertFalse(boundedTool.execute("""{"action":"search","query":"${"x".repeat(129)}"}""", emptyMap()).success)
        assertFalse(boundedTool.execute("""{"action":"get","name":"${"x".repeat(257)}"}""", emptyMap()).success)
        assertFalse(boundedTool.execute(" ".repeat(4_097), emptyMap()).success)
        assertEquals(0, reads)
    }
}
