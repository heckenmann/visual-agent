package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.part.OpaquePayload
import org.springframework.ai.chat.messages.part.ReasoningPart
import org.springframework.ai.chat.messages.part.TextPart
import org.springframework.ai.chat.messages.part.ToolCallPart
import org.springframework.ai.chat.messages.part.UnknownPart
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProviderReplayCodecTest {
    private val codec = ProviderReplayCodec()

    @Test
    fun `framework serialization preserves ordered parts encrypted reasoning and unknown blocks`() {
        val parts =
            listOf(
                ReasoningPart(
                    null,
                    "summary",
                    OpaquePayload("openai.responses", "encrypted_content", "opaque"),
                    mapOf("id" to "reasoning-1"),
                ),
                TextPart.of("text"),
                ToolCallPart.of(AssistantMessage.ToolCall("call-1", "function", "tool_help", "{}")),
                UnknownPart("openai.responses", "future", """{"type":"future","value":1}""", null, emptyMap()),
            )
        val original = AssistantMessage.builder().parts(parts).build()
        val encoded = codec.encode("profile", "model", original)

        val restored = codec.restore(encoded, "profile", "model", original)

        assertEquals(parts, restored?.parts)
        assertFalse(encoded.toString().contains("opaque"))
    }

    @Test
    fun `replay cannot cross profile or model boundaries`() {
        val message = AssistantMessage("text")
        val state = codec.encode("profile", "model", message)

        assertNull(codec.restore(state, "another-profile", "model", message))
        assertNull(codec.restore(state, "profile", "another-model", message))
    }

    @Test
    fun `restoring native parts never restores text removed by context budgeting`() {
        val state = codec.encode("profile", "model", AssistantMessage("long original text"))

        assertEquals("short", codec.restore(state, "profile", "model", AssistantMessage("short"))?.text)
    }
}
