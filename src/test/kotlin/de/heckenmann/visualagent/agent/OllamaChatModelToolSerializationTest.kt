package de.heckenmann.visualagent.agent

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaChatOptions
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [OllamaChatModel] does not send an empty `tools` array when
 * the prompt options omit [OllamaChatOptions.getToolCallbacks].
 */
class OllamaChatModelToolSerializationTest {
    @Test
    fun `chat model omits tools field when tool callbacks are not set`() {
        val ollamaApi = mockk<OllamaApi>()
        val requestSlot = slot<OllamaApi.ChatRequest>()
        every { ollamaApi.chat(capture(requestSlot)) } returns
            OllamaApi.ChatResponse(
                "response",
                Instant.now(),
                OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, "hello", null, null, null, null),
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
            )

        val chatModel =
            OllamaChatModel
                .builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder().model("m").build())
                .build()
        val prompt =
            Prompt(
                listOf(UserMessage("hi")),
                OllamaChatOptions.builder().model("m").build(),
            )

        chatModel.call(prompt)

        val request = requestSlot.captured
        assertTrue(request.tools().isNullOrEmpty(), "Expected no tools but got ${request.tools()}")
        verify(exactly = 1) { ollamaApi.chat(any()) }
    }

    @Test
    fun `chat model includes tools field when tool callbacks are explicitly empty`() {
        val ollamaApi = mockk<OllamaApi>()
        val requestSlot = slot<OllamaApi.ChatRequest>()
        every { ollamaApi.chat(capture(requestSlot)) } returns
            OllamaApi.ChatResponse(
                "response",
                Instant.now(),
                OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, "hello", null, null, null, null),
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
            )

        val chatModel =
            OllamaChatModel
                .builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder().model("m").build())
                .build()
        val options =
            OllamaChatOptions
                .builder()
                .model("m")
                .toolCallbacks(emptyList())
                .build()
        val prompt = Prompt(listOf(UserMessage("hi")), options)

        chatModel.call(prompt)

        val request = requestSlot.captured
        assertEquals(emptyList(), request.tools(), "Expected empty tools list but got ${request.tools()}")
    }
}
