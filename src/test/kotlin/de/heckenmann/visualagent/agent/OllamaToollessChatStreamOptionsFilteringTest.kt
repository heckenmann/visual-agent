package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaChatOptions
import reactor.core.publisher.Flux

/**
 * Verifies that [OllamaToollessChat.stream] also strips top-level keys
 * (`model`, `format`, `keep_alive`, `truncate`) from the `options` map
 * before placing them into the [OllamaApi.ChatRequest] record.
 */
class OllamaToollessChatStreamOptionsFilteringTest {
    @Test
    fun `stream strips model format keep_alive and truncate from options map`() =
        runTest {
            val ollamaApi = mockk<OllamaApi>()
            val requestSlot = slot<OllamaApi.ChatRequest>()

            every { ollamaApi.streamingChat(capture(requestSlot)) } returns
                Flux.just(
                    OllamaApi.ChatResponse(
                        "m",
                        java.time.Instant.now(),
                        OllamaApi.Message(
                            OllamaApi.Message.Role.ASSISTANT,
                            "hi",
                            null,
                            null,
                            null,
                            null,
                        ),
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                )

            val options =
                OllamaChatOptions
                    .builder()
                    .model("test-model")
                    .format("json")
                    .keepAlive("5m")
                    .truncate(true)
                    .temperature(0.7)
                    .build()

            val prompt =
                Prompt(
                    listOf(
                        org.springframework.ai.chat.messages
                            .SystemMessage("sys"),
                        org.springframework.ai.chat.messages
                            .UserMessage("hello"),
                    ),
                    options,
                )

            val promptFactory = mockk<OllamaPromptFactory>()
            every { promptFactory.buildPrompt(any(), any()) } returns prompt
            every { promptFactory.allowedFunctionNames(any(), any()) } returns emptyList()

            OllamaToollessChat
                .stream(
                    ollamaApi = ollamaApi,
                    promptFactory = promptFactory,
                    request = ChatRequestContext(messages = emptyList()),
                    selectedModel = "test-model",
                ).toList()

            val optionsMap = requestSlot.captured.options()
            assertFalse(optionsMap.containsKey("model"), "options must not contain 'model'")
            assertFalse(optionsMap.containsKey("format"), "options must not contain 'format'")
            assertFalse(optionsMap.containsKey("keep_alive"), "options must not contain 'keep_alive'")
            assertFalse(optionsMap.containsKey("truncate"), "options must not contain 'truncate'")
            assertTrue(optionsMap.containsKey("temperature"), "options must still contain 'temperature'")
            assertEquals(0.7, optionsMap["temperature"])
        }
}
