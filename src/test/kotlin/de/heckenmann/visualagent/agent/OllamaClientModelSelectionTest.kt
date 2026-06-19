package de.heckenmann.visualagent.agent.tests

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.OllamaClient
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import de.heckenmann.visualagent.agent.ollama.OllamaToolRecovery
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.toFunctionName
import de.heckenmann.visualagent.config.AppConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.ollama.api.OllamaApi
import reactor.core.publisher.Flux
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OllamaClientModelSelectionTest {
    @Test
    fun `chat uses selected model from app config`() =
        runTest {
            val previousModel = AppConfig.instance.ollamaModel
            AppConfig.instance.ollamaModel = "unit-test-model"
            try {
                val chatModel = mockk<ChatModel>()
                val ollamaApi = mockk<OllamaApi>(relaxed = true)
                every { chatModel.call(any<Prompt>()) } returns springResponse("unit-test-model", "ok")
                val client = createClient(chatModel, ollamaApi, ToolRegistry(emptyList(), ToolEventBus()))

                val response = client.chat(listOf(Message("user", "hello")))

                assertEquals("unit-test-model", response.model)
                assertEquals("ok", response.message.content)
                verify(exactly = 1) {
                    chatModel.call(
                        withArg<Prompt> { prompt ->
                            assertEquals("unit-test-model", prompt.options?.model)
                        },
                    )
                }
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
                val ollamaApi = mockk<OllamaApi>(relaxed = true)
                every { chatModel.stream(any<Prompt>()) } returns Flux.just(springResponse("stream-model", "chunk"))
                val client = createClient(chatModel, ollamaApi, ToolRegistry(emptyList(), ToolEventBus()))

                client.stream(listOf(Message("user", "hello"))).collect {}

                verify(exactly = 1) {
                    chatModel.stream(
                        withArg<Prompt> { prompt ->
                            assertEquals("stream-model", prompt.options?.model)
                        },
                    )
                }
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
                    ),
                )

            assertTrue(response.message.content.contains("Requested tool function does not exist"))
            assertTrue(response.message.content.contains("todos"))
            verify(exactly = 2) { chatModel.call(any<Prompt>()) }
        }

    @Test
    fun `chat surfaces detailed http response body in thrown error message`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>(relaxed = true)
            val registry = ToolRegistry(emptyList(), ToolEventBus())
            every { chatModel.call(any<Prompt>()) } throws
                ForbiddenLikeException(
                    "403 Forbidden from POST http://localhost:11434/api/chat",
                    "403 Forbidden: this model requires a subscription, upgrade for access",
                )
            val client = createClient(chatModel, ollamaApi, registry)

            val error =
                assertFailsWith<IllegalStateException> {
                    client.chat(listOf(Message("user", "hello")))
                }

            assertTrue(error.message.orEmpty().contains("403 Forbidden from POST"))
            assertTrue(error.message.orEmpty().contains("this model requires a subscription"))
        }

    private fun springResponse(
        model: String,
        content: String,
    ): org.springframework.ai.chat.model.ChatResponse =
        org.springframework.ai.chat.model.ChatResponse(
            listOf(Generation(AssistantMessage(content))),
            ChatResponseMetadata.builder().model(model).build(),
        )

    private fun createClient(
        chatModel: ChatModel,
        ollamaApi: OllamaApi,
        registry: ToolRegistry,
    ): OllamaClient {
        val promptFactory = OllamaPromptFactory(registry)
        val recovery = OllamaToolRecovery(chatModel, promptFactory)
        return OllamaClient(chatModel, ollamaApi, promptFactory, recovery)
    }

    private class FakeTool(
        id: String,
    ) : VisualAgentTool {
        override val definition =
            ToolDefinition(
                id = ToolId(id),
                name = ToolId(id).toFunctionName(),
                description = "Fake $id",
                inputSchema = """{"type":"object"}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = ToolResult(definition.id.value, true, "ok")
    }

    class ForbiddenLikeException(
        message: String,
        private val body: String,
    ) : RuntimeException(message) {
        @Suppress("unused")
        fun getResponseBodyAsString(): String = body
    }
}
