package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.ollama.api.OllamaApi
import reactor.core.publisher.Flux
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaClientModelSelectionTest {
    @Test
    fun `chat uses selected model from app config`() =
        runTest {
            val previousModel = AppConfig.instance.ollamaModel
            AppConfig.instance.ollamaModel = "unit-test-model"
            try {
                val chatModel = mockk<ChatModel>()
                val ollamaApi = mockk<OllamaApi>()
                val requestSlot = io.mockk.slot<OllamaApi.ChatRequest>()
                every { ollamaApi.chat(capture(requestSlot)) } returns
                    OllamaApi.ChatResponse(
                        "unit-test-model",
                        Instant.now(),
                        OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, "ok", null, null, null, null),
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    )
                val client = createClient(chatModel, ollamaApi, ToolRegistry(emptyList(), ToolEventBus()))

                val response = client.chat(listOf(Message("user", "hello")))

                assertEquals("unit-test-model", response.model)
                assertEquals("ok", response.message.content)
                assertEquals("unit-test-model", requestSlot.captured.model())
            } finally {
                AppConfig.instance.ollamaModel = previousModel
            }
        }

    @Test
    fun `stream uses selected model from app config`() =
        runTest {
            val previousModel = AppConfig.instance.ollamaModel
            AppConfig.instance.ollamaModel = "stream-model"
            try {
                val chatModel = mockk<ChatModel>()
                val ollamaApi = mockk<OllamaApi>()
                every { ollamaApi.streamingChat(any()) } returns
                    Flux.just(
                        OllamaApi.ChatResponse(
                            "stream-model",
                            Instant.now(),
                            OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, "chunk", null, null, null, null),
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
                val client = createClient(chatModel, ollamaApi, ToolRegistry(emptyList(), ToolEventBus()))

                client.stream(listOf(Message("user", "hello"))).collect {}

                verify(exactly = 1) { ollamaApi.streamingChat(any()) }
            } finally {
                AppConfig.instance.ollamaModel = previousModel
            }
        }

    @Test
    fun `chat attaches enabled tool callbacks to spring ai prompt`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>(relaxed = true)
            val registry = ToolRegistry(listOf(FakeTool("context"), FakeTool("terminal")), ToolEventBus())
            every { chatModel.call(any<Prompt>()) } answers {
                val options = firstArg<Prompt>().options as ToolCallingChatOptions
                assertEquals(listOf("context"), options.toolCallbacks.orEmpty().map { it.toolDefinition.name() })
                assertTrue(options.toolContext.orEmpty().isNotEmpty())
                val firstMessage = firstArg<Prompt>().instructions.first()
                assertTrue(firstMessage.text.orEmpty().contains("Tool calling strict mode"))
                assertTrue(firstMessage.text.orEmpty().contains("context"))
                assertTrue(
                    options.toolCallbacks
                        .orEmpty()
                        .single()
                        .call("""{}""")
                        .contains("context"),
                )
                springResponse("tool-model", "tool-ready")
            }
            val client = createClient(chatModel, ollamaApi, registry)

            val response =
                client.chat(
                    ChatRequestContext(
                        messages = listOf(Message("user", "use context")),
                        model = "tool-model",
                        enabledTools = setOf(ToolId("context")),
                        modelCapabilities = setOf("tools"),
                    ),
                )

            assertEquals("tool-ready", response.message.content)
        }

    @Test
    fun `chat retries with structured tool error context when unknown function callback is requested`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>(relaxed = true)
            val registry = ToolRegistry(listOf(FakeTool("todos")), ToolEventBus())
            every { chatModel.call(any<Prompt>()) } answers {
                val prompt = firstArg<Prompt>()
                val options = prompt.options as ToolCallingChatOptions
                if (
                    options.toolCallbacks.orEmpty().isNotEmpty() &&
                    prompt.instructions.none { it.text.orEmpty().contains("Error type: tool-error") }
                ) {
                    throw IllegalStateException("No function callback found for function name: todo")
                }
                springResponse("tool-model", "Recovered after unknown tool error")
            }
            val client = createClient(chatModel, ollamaApi, registry)

            val response =
                client.chat(
                    ChatRequestContext(
                        messages = listOf(Message("user", "list todos")),
                        model = "tool-model",
                        enabledTools = setOf(ToolId("todos")),
                        modelCapabilities = setOf("tools"),
                    ),
                )

            assertEquals("Recovered after unknown tool error", response.message.content)
            verify(exactly = 2) { chatModel.call(any<Prompt>()) }
        }

    @Test
    fun `stream retries with structured tool error context when unknown function callback is requested`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>(relaxed = true)
            val registry = ToolRegistry(listOf(FakeTool("todos")), ToolEventBus())
            every { chatModel.stream(any<Prompt>()) } throws
                IllegalStateException(
                    "No function callback found for function name: todo:list",
                )
            every { chatModel.call(any<Prompt>()) } returns springResponse("tool-model", "Recovered stream fallback")
            val client = createClient(chatModel, ollamaApi, registry)

            val chunks =
                client
                    .stream(
                        ChatRequestContext(
                            messages = listOf(Message("user", "list todos")),
                            model = "tool-model",
                            enabledTools = setOf(ToolId("todos")),
                            modelCapabilities = setOf("tools"),
                        ),
                    ).toList()

            assertEquals(1, chunks.size)
            assertEquals("Recovered stream fallback", chunks.single().message.content)
            verify(exactly = 1) { chatModel.stream(any<Prompt>()) }
            verify(exactly = 1) { chatModel.call(any<Prompt>()) }
        }

    @Test
    fun `chat falls back to tool list when recovery answer remains blank`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>(relaxed = true)
            val registry = ToolRegistry(listOf(FakeTool("todos")), ToolEventBus())
            every { chatModel.call(any<Prompt>()) } throws
                IllegalStateException("No function callback found for function name: todo:list")
            val client = createClient(chatModel, ollamaApi, registry)

            val response =
                client.chat(
                    ChatRequestContext(
                        messages = listOf(Message("user", "list todos")),
                        model = "tool-model",
                        enabledTools = setOf(ToolId("todos")),
                        modelCapabilities = setOf("tools"),
                    ),
                )

            assertTrue(response.message.content.contains("Requested tool function does not exist"))
            assertTrue(response.message.content.contains("todos"))
            verify(exactly = 2) { chatModel.call(any<Prompt>()) }
        }

    @Test
    fun `vision sends image media through chat model`() =
        runTest {
            val previousModel = AppConfig.instance.ollamaModel
            AppConfig.instance.ollamaModel = "vision-model"
            try {
                val chatModel = mockk<ChatModel>()
                val ollamaApi = mockk<OllamaApi>(relaxed = true)
                val promptSlot = io.mockk.slot<Prompt>()
                every { chatModel.call(capture(promptSlot)) } returns springResponse("vision-model", "image description")
                val client = createClient(chatModel, ollamaApi, ToolRegistry(emptyList(), ToolEventBus()))

                val response = client.vision(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), "describe")

                val message = promptSlot.captured.instructions.single() as UserMessage
                assertEquals("image description", response.message.content)
                assertEquals("describe", message.text)
                assertEquals(1, message.media.size)
            } finally {
                AppConfig.instance.ollamaModel = previousModel
            }
        }

    @Test
    fun `ollama api helpers expose embeddings models and details`() =
        runTest {
            val chatModel = mockk<ChatModel>(relaxed = true)
            val ollamaApi = mockk<OllamaApi>()
            val details = OllamaApi.Model.Details("parent", "gguf", "llama", listOf("llama"), "7B", "Q4")
            every { ollamaApi.embed(any()) } returns OllamaApi.EmbeddingsResponse("embed", listOf(floatArrayOf(1f, 2f)), 1L, 1L, 1)
            every { ollamaApi.listModels() } returns
                OllamaApi.ListModelResponse(
                    listOf(OllamaApi.Model("llama", "llama", Instant.EPOCH, 1L, "digest", details)),
                )
            every { ollamaApi.showModel(any()) } returns
                OllamaApi.ShowModelResponse(
                    "license",
                    "modelfile",
                    "parameters",
                    "template",
                    "system",
                    details,
                    emptyList(),
                    emptyMap(),
                    emptyMap(),
                    listOf("vision"),
                    Instant.EPOCH,
                )
            val client = createClient(chatModel, ollamaApi, ToolRegistry(emptyList(), ToolEventBus()))

            assertEquals(listOf(1.0, 2.0), client.embeddings("hello"))
            assertEquals(listOf("llama"), client.getModels())
            val modelDetails = client.getModelDetails("llama")
            assertEquals("llama", modelDetails.details?.family)
            assertEquals("7B", modelDetails.details?.parameterSize)
        }
}
