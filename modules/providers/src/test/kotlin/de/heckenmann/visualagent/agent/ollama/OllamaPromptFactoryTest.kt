package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ContextPolicyMetadata
import de.heckenmann.visualagent.agent.ContextWindow
import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelParameters
import de.heckenmann.visualagent.agent.TestToolRegistry
import de.heckenmann.visualagent.agent.TestVisualAgentTool
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.toTestFunctionName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.ollama.api.OllamaChatOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [OllamaPromptFactory].
 */
class OllamaPromptFactoryTest {
    @Test
    fun `buildPrompt preserves context policy metadata for subsequent budget passes`() {
        val factory = OllamaPromptFactory(TestToolRegistry())
        val request =
            ChatRequestContext(
                messages =
                    listOf(
                        Message("user", "prior question"),
                        Message("assistant", "summary", contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE),
                        Message("user", "latest question"),
                    ),
            )

        val prompt = factory.buildPrompt(request, "ollama-test")
        val summary = prompt.instructions.filterIsInstance<AssistantMessage>().single()

        assertEquals(ConversationContextPolicy.SUMMARY_SOURCE.name, summary.metadata[ContextPolicyMetadata.KEY])
    }

    @Test
    fun `buildPrompt omits tool options when model lacks tools capability`() {
        val registry = TestToolRegistry(listOf(FakeTool("todos")))
        val factory = OllamaPromptFactory(registry)
        val request =
            ChatRequestContext(
                messages = listOf(Message("user", "hello")),
                model = "no-tools-model",
                enabledTools = setOf(ToolId("todos")),
                modelCapabilities = setOf("completion"),
                modelCapabilitiesComplete = true,
            )

        val prompt = factory.buildPrompt(request, "no-tools-model")
        val options = prompt.options as OllamaChatOptions

        assertTrue(options.toolCallbacks.orEmpty().isEmpty(), "toolCallbacks must be empty")
        assertTrue(options.toolContext.orEmpty().isEmpty(), "toolContext must be empty")
        assertTrue(prompt.instructions.none { it.text.orEmpty().contains("Tool calling strict mode") })
    }

    @Test
    fun `buildPrompt exposes only provider function names in callbacks and strict guard`() {
        val registry = TestToolRegistry(listOf(FakeTool("workspace:file")))
        val factory = OllamaPromptFactory(registry)
        val request =
            ChatRequestContext(
                messages = listOf(Message("user", "hello")),
                model = "tools-model",
                enabledTools = setOf(ToolId("workspace:file")),
                modelCapabilities = setOf("tools"),
                modelCapabilitiesComplete = true,
            )

        val prompt = factory.buildPrompt(request, "tools-model")
        val options = prompt.options as OllamaChatOptions

        assertEquals(listOf("workspace_file"), options.toolCallbacks.orEmpty().map { it.toolDefinition.name() })
        assertEquals("tools-model", options.toolContext?.get("model"))
        val guard =
            prompt.instructions
                .first()
                .text
                .orEmpty()
        assertTrue(guard.contains("workspace_file"))
        assertTrue(!guard.contains("workspace:file"))
    }

    @Test
    fun `buildPrompt omits tool options when model supports tools but no tools are enabled`() {
        val registry = TestToolRegistry(listOf(FakeTool("todos")))
        val factory = OllamaPromptFactory(registry)
        val request =
            ChatRequestContext(
                messages = listOf(Message("user", "hello")),
                model = "tools-model",
                enabledTools = emptySet(),
                modelCapabilities = setOf("tools"),
                modelCapabilitiesComplete = true,
            )

        val prompt = factory.buildPrompt(request, "tools-model")
        val options = prompt.options as OllamaChatOptions

        assertTrue(options.toolCallbacks.orEmpty().isEmpty(), "toolCallbacks must be empty when no tools enabled")
        assertTrue(options.toolContext.orEmpty().isEmpty(), "toolContext must be empty when no tools enabled")
    }

    @Test
    fun `falls back to tool help rather than displacing assistant history`() {
        val registry = TestToolRegistry(listOf(FakeTool("tool:help"), FakeTool("workspace:file", "x".repeat(5_000))))
        val factory = OllamaPromptFactory(registry)
        val request =
            ChatRequestContext(
                messages =
                    listOf(
                        Message("system", "rules"),
                        Message("user", "previous question"),
                        Message("assistant", "previous answer"),
                        Message("user", "latest question"),
                    ),
                parameters = ModelParameters(maxTokens = 40),
                contextWindow = ContextWindow(configuredLimit = 512),
                enabledTools = setOf(ToolId("tool:help"), ToolId("workspace:file")),
                modelCapabilities = setOf("tools"),
                modelCapabilitiesComplete = true,
            )

        val prompt = factory.buildPrompt(request, "ollama-test")
        val options = prompt.options as OllamaChatOptions

        assertEquals(listOf("tool_help"), options.toolCallbacks.orEmpty().map { it.toolDefinition.name() })
        assertTrue(prompt.instructions.any { it.text == "previous answer" })
        assertTrue(prompt.instructions.any { it.text == "latest question" })
        assertTrue(prompt.instructions.any { it.text.orEmpty().contains("tool_help with {\"action\":\"list\"}") })
    }

    private class FakeTool(
        id: String,
        schemaPadding: String = "",
    ) : TestVisualAgentTool {
        override val definition =
            ToolDefinition(
                id = ToolId(id),
                name = ToolId(id).toTestFunctionName(),
                description = "Fake $id $schemaPadding",
                inputSchema = """{"type":"object","description":"$schemaPadding"}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = ToolResult(definition.id.value, true, "ok")
    }
}
