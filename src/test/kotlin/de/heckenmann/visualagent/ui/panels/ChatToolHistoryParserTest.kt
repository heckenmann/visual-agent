package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.Message
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
}
