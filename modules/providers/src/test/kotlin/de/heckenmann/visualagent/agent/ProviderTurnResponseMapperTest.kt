package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.ollama.api.OllamaApi
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

/**
 * Verifies provider-neutral response normalization without exposing raw SDK data.
 */
class ProviderTurnResponseMapperTest {
    @Test
    fun `maps spring tool calls with stable provider IDs and typed termination`() {
        val response =
            SpringChatResponse(
                listOf(
                    Generation(
                        AssistantMessage
                            .builder()
                            .content("")
                            .toolCalls(
                                listOf(
                                    AssistantMessage.ToolCall("call-7", "function", "read_file", "{\"path\":\"a.txt\"}"),
                                    AssistantMessage.ToolCall("", "function", "read_file", "{\"path\":\"b.txt\"}"),
                                ),
                            ).build(),
                        ChatGenerationMetadata.builder().finishReason("tool_calls").build(),
                    ),
                ),
                ChatResponseMetadata
                    .builder()
                    .id("response-3")
                    .model("model-a")
                    .build(),
            )

        val turn = ProviderTurnResponseMapper.fromSpring(response, requestId = "request-4", round = 1, sequence = 2)

        assertEquals(ProviderFinishReason.TOOL_CALLS, turn.finishReason)
        assertEquals("call-7", turn.toolCalls[0].id)
        assertEquals("fallback-tool-call-request-4-1-2-1", turn.toolCalls[1].id)
        assertEquals("response-3", turn.metadata.responseId)
        assertEquals("tool_calls", turn.metadata.rawFinishReason)
    }

    @Test
    fun `maps native ollama thinking and nanosecond timings without leaking them into content`() {
        val response =
            OllamaApi.ChatResponse(
                "qwen3",
                Instant.parse("2026-08-27T10:15:30Z"),
                OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, "answer", null, null, null, "brief reasoning"),
                "stop",
                true,
                2_400_000_000L,
                null,
                12,
                800_000_000L,
                5,
                1_600_000_000L,
            )

        val turn = ProviderTurnResponseMapper.fromOllama(response, requestId = "request-5")

        assertEquals("answer", turn.content)
        assertEquals("brief reasoning", turn.reasoning)
        assertFalse(turn.reasoningIsSummary)
        assertEquals(ProviderFinishReason.STOP, turn.finishReason)
        assertEquals(2_400L, turn.timing?.totalMillis)
        assertEquals(800L, turn.timing?.promptEvaluationMillis)
        assertEquals(1_600L, turn.timing?.generationMillis)
        assertEquals(17, turn.usage?.totalTokens)
        assertNull(ProviderTurnResponseMapper.toChatResponse(turn).message.metadata)
        assertEquals(2_400_000_000L, ProviderTurnResponseMapper.toChatResponse(turn).totalDuration)
    }

    @Test
    fun `keeps unfinished spring chunks nonterminal`() {
        val response =
            SpringChatResponse(
                listOf(Generation(AssistantMessage("partial"), ChatGenerationMetadata.builder().build())),
                ChatResponseMetadata.builder().model("model-a").build(),
            )

        val turn = ProviderTurnResponseMapper.fromSpring(response)

        assertNull(turn.finishReason)
        assertFalse(ProviderTurnResponseMapper.toChatResponse(turn).done)
    }
}
