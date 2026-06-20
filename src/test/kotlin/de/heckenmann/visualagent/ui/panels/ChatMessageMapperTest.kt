package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.ui.panels.chat.ChatMessageMapper
import de.heckenmann.visualagent.ui.panels.chat.ChatToolHistoryParser
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMessageMapperTest {
    private val mapper = ChatMessageMapper(ChatToolHistoryParser())

    @Test
    fun `history mapping preserves content and tool metadata`() {
        assertNull(mapper.fromHistory(Message("assistant", " ")))
        val plain = mapper.fromHistory(Message("user", "hello"))
        val tool =
            mapper.fromHistory(
                Message(
                    "assistant",
                    "tool result",
                    """{"type":"tool_call","toolId":"todos","status":"ok","durationMillis":4}""",
                ),
            )

        assertEquals("hello", plain?.content)
        assertFalse(plain?.isToolEvent ?: true)
        assertTrue(tool?.isToolEvent == true)
        assertEquals("todos", tool?.toolData?.toolId)
    }

    @Test
    fun `history mapping preserves immutable image metadata`() {
        val image =
            mapper.fromHistory(
                Message(
                    "assistant",
                    "Canvas snapshot (PNG)",
                    """{"type":"image","source":"canvas","mimeType":"image/png","dataUrl":"data:image/png;base64,AQID","width":2,"height":1,"immutable":true}""",
                ),
            )

        assertEquals("Canvas snapshot (PNG)", image?.content)
        assertEquals("image/png", image?.imageData?.mimeType)
        assertEquals("data:image/png;base64,AQID", image?.imageData?.dataUrl)
        assertEquals(2, image?.imageData?.width)
        assertEquals(1, image?.imageData?.height)
    }

    @Test
    fun `history mapping normalizes legacy recovery provider errors`() {
        val auth =
            mapper.fromHistory(
                Message(
                    "assistant",
                    "Recovery note: Could not auto-resume interrupted request (OpenAI API key is not configured).",
                ),
            )
        val subscription =
            mapper.fromHistory(
                Message(
                    "assistant",
                    """Recovery note: Could not auto-resume interrupted request (HTTP 403 - {"error":"upgrade for access"}).""",
                ),
            )

        assertEquals(
            "I could not resume the previous request automatically. Authentication failed. Check the provider API key and base URL in Session settings.",
            auth?.content,
        )
        assertEquals(
            "I could not resume the previous request automatically. The selected model is not available for this account. Choose another model or update the provider subscription.",
            subscription?.content,
        )
    }

    @Test
    fun `tool event mapping handles success failure thinking and empty payloads`() {
        val success = mapper.fromToolEvent(event("file:read", true, "first line\nsecond", null))
        val failure = mapper.fromToolEvent(event("terminal", false, "", "Exit 1"))
        val thinking = mapper.fromToolEvent(event("thinking", true, "", null))
        val empty = mapper.fromToolEvent(event("context", true, "", null))

        assertTrue(success.content.contains("first line"))
        assertEquals("ok", success.toolData?.status)
        assertTrue(failure.content.contains("Exit 1"))
        assertEquals("error", failure.toolData?.status)
        assertEquals("thinking", thinking.toolData?.status)
        assertEquals("Tool context (7ms) · ok", empty.content)
    }

    private fun event(
        toolId: String,
        success: Boolean,
        content: String,
        error: String?,
    ) = ToolCallEvent(
        toolId = toolId,
        functionName = toolId.replace(':', '_'),
        inputJson = "{}",
        context = emptyMap(),
        result = ToolResult(toolId, success, content, error),
        startedAtUtc = Instant.EPOCH,
        finishedAtUtc = Instant.EPOCH,
        durationMillis = 7,
    )
}
