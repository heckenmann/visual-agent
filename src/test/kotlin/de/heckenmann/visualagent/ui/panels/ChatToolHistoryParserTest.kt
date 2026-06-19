package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.ui.panels.chat.ChatToolHistoryParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests robust tool metadata parsing for conversation history rendering.
 */
class ChatToolHistoryParserTest {
    @Test
    fun `tool history detection works with formatted metadata json`() {
        val parser = ChatToolHistoryParser()
        val message =
            Message(
                role = "assistant",
                content = "Tool todos · ok",
                metadata =
                    """
                    {
                      "type": "tool_call",
                      "toolId": "todos",
                      "status": "ok",
                      "durationMillis": 12
                    }
                    """.trimIndent(),
            )

        assertTrue(parser.isToolHistoryEntry(message))
        val data = assertNotNull(parser.parseToolMetadata(message.metadata!!))
        assertEquals("todos", data.toolId)
        assertEquals("ok", data.status)
        assertEquals(12L, data.durationMillis)
    }

    @Test
    fun `invalid and unrelated metadata are ignored`() {
        val parser = ChatToolHistoryParser()

        assertFalse(parser.isToolHistoryEntry(Message("assistant", "plain")))
        assertNull(parser.parseToolMetadata("not-json"))
        assertNull(parser.parseToolMetadata("[]"))
        assertNull(parser.parseToolMetadata("""{"type":"other"}"""))
        assertNull(parser.parseToolMetadata("""{"status":"ok"}"""))
    }

    @Test
    fun `missing optional tool fields use safe defaults`() {
        val data = assertNotNull(ChatToolHistoryParser().parseToolMetadata("""{"type":"tool_call"}"""))

        assertEquals("tool", data.toolId)
        assertEquals("ok", data.status)
        assertNull(data.durationMillis)
    }
}
