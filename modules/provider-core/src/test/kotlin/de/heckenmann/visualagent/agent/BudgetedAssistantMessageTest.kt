package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.part.TextPart
import org.springframework.ai.chat.messages.part.ToolCallPart
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BudgetedAssistantMessageTest {
    @Test
    fun `replaces all text while preserving ordered tool parts and metadata`() {
        val call = ToolCallPart.of(AssistantMessage.ToolCall("call-1", "function", "tool_help", "{}"))
        val message =
            AssistantMessage
                .builder()
                .parts(listOf(TextPart.of("old one"), call, TextPart.of("old two")))
                .properties(mapOf("turn" to "turn-1"))
                .build()

        val rewritten = message.withBudgetedText("bounded")

        assertEquals("bounded", rewritten.text)
        assertEquals(2, rewritten.parts.size)
        assertSame(call, rewritten.parts[1])
        assertEquals(message.metadata, rewritten.metadata)
        assertEquals("old oneold two", message.text)
    }

    @Test
    fun `adds replacement text to an assistant message without text`() {
        val call = ToolCallPart.of(AssistantMessage.ToolCall("call-1", "function", "tool_help", "{}"))
        val message = AssistantMessage.builder().part(call).build()

        val rewritten = message.withBudgetedText("bounded")

        assertEquals("bounded", rewritten.text)
        assertSame(call, rewritten.parts[1])
    }
}
