package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfigBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.ollama.api.OllamaApi
import reactor.core.publisher.Flux
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the model-capability-aware tool-callback gating in [OllamaClient].
 *
 * When a model lacks the "tools" capability, tool callbacks must not be sent
 * to the Ollama API, otherwise the server returns a 500 error.
 */
class OllamaClientCapabilityGatingTest {
    @Test
    fun `chat skips tool callbacks when model lacks tools capability`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>()
            val registry = ToolRegistry(listOf(FakeTool("context")), ToolEventBus(), AppConfigBean())
            every { ollamaApi.chat(any()) } returns
                OllamaApi.ChatResponse(
                    "no-tools-model",
                    Instant.now(),
                    OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, "plain answer", null, null, null, null),
                    null,
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                )
            val client = createClient(chatModel, ollamaApi, registry)

            val response =
                client.chat(
                    ChatRequestContext(
                        messages = listOf(Message("user", "hello")),
                        model = "no-tools-model",
                        enabledTools = setOf(ToolId("context")),
                        modelCapabilities = emptySet(),
                    ),
                )

            assertEquals("plain answer", response.message.content)
            verify(exactly = 0) { chatModel.call(any<Prompt>()) }
            verify(exactly = 1) { ollamaApi.chat(any()) }
        }

    @Test
    fun `stream skips tool callbacks when model lacks tools capability`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>()
            val registry = ToolRegistry(listOf(FakeTool("context")), ToolEventBus(), AppConfigBean())
            every { ollamaApi.streamingChat(any()) } returns
                Flux.just(
                    OllamaApi.ChatResponse(
                        "no-tools-model",
                        Instant.now(),
                        OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, "stream chunk", null, null, null, null),
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
            val client = createClient(chatModel, ollamaApi, registry)

            val response =
                client.stream(
                    ChatRequestContext(
                        messages = listOf(Message("user", "hello")),
                        model = "no-tools-model",
                        enabledTools = setOf(ToolId("context")),
                        modelCapabilities = emptySet(),
                    ),
                )
            val chunks = response.toList()

            assertTrue(chunks.isNotEmpty())
            verify(exactly = 0) { chatModel.call(any<Prompt>()) }
            verify(exactly = 0) { chatModel.stream(any<Prompt>()) }
            verify(exactly = 1) { ollamaApi.streamingChat(any()) }
        }

    @Test
    fun `chat includes tool callbacks when model supports tools`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>(relaxed = true)
            val registry = ToolRegistry(listOf(FakeTool("context")), ToolEventBus(), AppConfigBean())
            every { chatModel.call(any<Prompt>()) } answers {
                val options = firstArg<Prompt>().options as ToolCallingChatOptions
                assertTrue(options.toolCallbacks.orEmpty().isNotEmpty())
                springResponse("tools-model", "tool answer")
            }
            val client = createClient(chatModel, ollamaApi, registry)

            val response =
                client.chat(
                    ChatRequestContext(
                        messages = listOf(Message("user", "hello")),
                        model = "tools-model",
                        enabledTools = setOf(ToolId("context")),
                        modelCapabilities = setOf("completion", "tools"),
                    ),
                )

            assertEquals("tool answer", response.message.content)
            verify(exactly = 1) { chatModel.call(any<Prompt>()) }
        }
}
