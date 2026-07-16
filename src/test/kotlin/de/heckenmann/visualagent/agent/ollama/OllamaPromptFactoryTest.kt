package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.toFunctionName
import de.heckenmann.visualagent.config.AppConfigBean
import org.junit.jupiter.api.Test
import org.springframework.ai.ollama.api.OllamaChatOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [OllamaPromptFactory].
 */
class OllamaPromptFactoryTest {
    @Test
    fun `buildPrompt omits tool options when model lacks tools capability`() {
        val registry = ToolRegistry(listOf(FakeTool("todos")), ToolEventBus(), AppConfigBean())
        val factory = OllamaPromptFactory(registry)
        val request =
            ChatRequestContext(
                messages = listOf(Message("user", "hello")),
                model = "no-tools-model",
                enabledTools = setOf(ToolId("todos")),
                modelCapabilities = emptySet(),
            )

        val prompt = factory.buildPrompt(request, "no-tools-model")
        val options = prompt.options as OllamaChatOptions

        assertTrue(options.toolCallbacks.orEmpty().isEmpty(), "toolCallbacks must be empty")
        assertTrue(options.toolContext.orEmpty().isEmpty(), "toolContext must be empty")
    }

    @Test
    fun `buildPrompt includes tool options when model supports tools and tools are enabled`() {
        val registry = ToolRegistry(listOf(FakeTool("todos")), ToolEventBus(), AppConfigBean())
        val factory = OllamaPromptFactory(registry)
        val request =
            ChatRequestContext(
                messages = listOf(Message("user", "hello")),
                model = "tools-model",
                enabledTools = setOf(ToolId("todos")),
                modelCapabilities = setOf("tools"),
            )

        val prompt = factory.buildPrompt(request, "tools-model")
        val options = prompt.options as OllamaChatOptions

        assertEquals(listOf("todos"), options.toolCallbacks.orEmpty().map { it.toolDefinition.name() })
        assertEquals("tools-model", options.toolContext?.get("model"))
    }

    @Test
    fun `buildPrompt omits tool options when model supports tools but no tools are enabled`() {
        val registry = ToolRegistry(listOf(FakeTool("todos")), ToolEventBus(), AppConfigBean())
        val factory = OllamaPromptFactory(registry)
        val request =
            ChatRequestContext(
                messages = listOf(Message("user", "hello")),
                model = "tools-model",
                enabledTools = emptySet(),
                modelCapabilities = setOf("tools"),
            )

        val prompt = factory.buildPrompt(request, "tools-model")
        val options = prompt.options as OllamaChatOptions

        assertTrue(options.toolCallbacks.orEmpty().isEmpty(), "toolCallbacks must be empty when no tools enabled")
        assertTrue(options.toolContext.orEmpty().isEmpty(), "toolContext must be empty when no tools enabled")
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
}
