package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.toFunctionName
import de.heckenmann.visualagent.config.AppConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.function.FunctionCallingOptions
import org.springframework.ai.ollama.api.OllamaApi
import reactor.core.publisher.Flux
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaClientModelSelectionTest {

    @Test
    fun `chat uses selected model from app config`() = runTest {
        val previousModel = AppConfig.instance.ollamaModel
        AppConfig.instance.ollamaModel = "unit-test-model"
        try {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>(relaxed = true)
            every { chatModel.call(any<Prompt>()) } returns springResponse("unit-test-model", "ok")
            val client = OllamaClient(chatModel, ollamaApi, ToolRegistry(emptyList(), ToolEventBus()))

            val response = client.chat(listOf(Message("user", "hello")))

            assertEquals("unit-test-model", response.model)
            assertEquals("ok", response.message.content)
            verify(exactly = 1) {
                chatModel.call(withArg<Prompt> { prompt ->
                    assertEquals("unit-test-model", prompt.options.model)
                })
            }
        } finally {
            AppConfig.instance.ollamaModel = previousModel
        }
    }

    @Test
    fun `stream uses selected model from app config`() = runTest {
        val previousModel = AppConfig.instance.ollamaModel
        AppConfig.instance.ollamaModel = "stream-model"
        try {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>(relaxed = true)
            every { chatModel.stream(any<Prompt>()) } returns Flux.just(springResponse("stream-model", "chunk"))
            val client = OllamaClient(chatModel, ollamaApi, ToolRegistry(emptyList(), ToolEventBus()))

            client.stream(listOf(Message("user", "hello"))).collect {}

            verify(exactly = 1) {
                chatModel.stream(withArg<Prompt> { prompt ->
                    assertEquals("stream-model", prompt.options.model)
                })
            }
        } finally {
            AppConfig.instance.ollamaModel = previousModel
        }
    }

    @Test
    fun `chat attaches enabled tool callbacks to spring ai prompt`() = runTest {
        val chatModel = mockk<ChatModel>()
        val ollamaApi = mockk<OllamaApi>(relaxed = true)
        val registry = ToolRegistry(listOf(FakeTool("context"), FakeTool("terminal")), ToolEventBus())
        every { chatModel.call(any<Prompt>()) } answers {
            val options = firstArg<Prompt>().options as FunctionCallingOptions
            assertEquals(setOf("context"), options.functions)
            assertEquals(listOf("context"), options.functionCallbacks.map { it.name })
            assertTrue(options.toolContext.isNullOrEmpty())
            assertTrue(options.functionCallbacks.single().call("""{}""").contains("context"))
            springResponse("tool-model", "tool-ready")
        }
        val client = OllamaClient(chatModel, ollamaApi, registry)

        val response = client.chat(
            ChatRequestContext(
                messages = listOf(Message("user", "use context")),
                model = "tool-model",
                enabledTools = setOf(ToolId("context")),
            ),
        )

        assertEquals("tool-ready", response.message.content)
    }

    private fun springResponse(model: String, content: String): org.springframework.ai.chat.model.ChatResponse =
        org.springframework.ai.chat.model.ChatResponse(
            listOf(Generation(AssistantMessage(content))),
            ChatResponseMetadata.builder().model(model).build(),
        )

    private class FakeTool(id: String) : VisualAgentTool {
        override val definition = ToolDefinition(
            id = ToolId(id),
            name = ToolId(id).toFunctionName(),
            description = "Fake $id",
            inputSchema = """{"type":"object"}""",
        )

        override fun execute(inputJson: String, context: Map<String, Any>): ToolResult =
            ToolResult(definition.id.value, true, "ok")
    }
}
