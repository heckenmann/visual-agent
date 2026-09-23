package de.heckenmann.visualagent.agent.openai

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.TestToolRegistry
import de.heckenmann.visualagent.agent.TestVisualAgentTool
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.toTestFunctionName
import org.junit.jupiter.api.Test
import org.springframework.ai.model.tool.ToolCallingChatOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiPromptFactoryTest {
    @Test
    fun `prompt exposes only provider function names in callbacks and strict guard`() {
        val registry = TestToolRegistry(listOf(FakeTool("agent:list"), FakeTool("terminal")))
        val factory = OpenAiPromptFactory(registry)

        val prompt =
            factory.buildPrompt(
                ChatRequestContext(
                    messages = listOf(Message("user", "list agents")),
                    enabledTools = setOf(ToolId("agent:list")),
                    modelCapabilities = setOf("tools"),
                    modelCapabilitiesComplete = true,
                ),
                "gpt-test",
            )
        val options = prompt.options as ToolCallingChatOptions

        assertEquals("gpt-test", prompt.options?.model)
        assertEquals(listOf("agent_list"), options.toolCallbacks.orEmpty().map { it.toolDefinition.name() })
        val guard =
            prompt.instructions
                .first()
                .text
                .orEmpty()
        assertTrue(guard.contains("Tool calling strict mode"))
        assertTrue(guard.contains("agent_list"))
        assertTrue(!guard.contains("agent:list"))
        assertEquals(
            "openai",
            options.toolContext.orEmpty()["provider"].toString(),
        )
    }

    @Test
    fun `prompt omits guard when no tools enabled`() {
        val factory = OpenAiPromptFactory(TestToolRegistry())

        val prompt = factory.buildPrompt(ChatRequestContext(messages = listOf(Message("user", "hi"))), "gpt-test")

        assertTrue(prompt.instructions.none { it.text.orEmpty().contains("Tool calling strict mode") })
    }

    @Test
    fun `prompt omits tools and guard when model explicitly lacks tooling`() {
        val registry = TestToolRegistry(listOf(FakeTool("context")))
        val factory = OpenAiPromptFactory(registry)

        val prompt =
            factory.buildPrompt(
                ChatRequestContext(
                    messages = listOf(Message("user", "show context")),
                    enabledTools = setOf(ToolId("context")),
                    modelCapabilities = setOf("completion"),
                    modelCapabilitiesComplete = true,
                ),
                "no-tools-model",
            )

        assertTrue(prompt.instructions.none { it.text.orEmpty().contains("Tool calling strict mode") })
        val options = prompt.options as org.springframework.ai.openai.OpenAiChatOptions
        assertTrue(options.toolCallbacks.orEmpty().isEmpty())
        assertTrue(options.toolContext.orEmpty().isEmpty())
        assertEquals(emptyList(), factory.allowedFunctionNames(promptRequest(), "no-tools-model"))
    }

    @Test
    fun `allowedFunctionNames returns sorted enabled names`() {
        val registry = TestToolRegistry(listOf(FakeTool("terminal"), FakeTool("context")))
        val factory = OpenAiPromptFactory(registry)

        val names =
            factory.allowedFunctionNames(
                ChatRequestContext(
                    messages = emptyList(),
                    enabledTools = setOf(ToolId("terminal"), ToolId("context")),
                    modelCapabilities = setOf("tools"),
                    modelCapabilitiesComplete = true,
                ),
                "gpt-test",
            )

        assertEquals(listOf("context", "terminal"), names)
    }

    private fun promptRequest() =
        ChatRequestContext(
            messages = emptyList(),
            enabledTools = setOf(ToolId("context")),
            modelCapabilities = setOf("completion"),
            modelCapabilitiesComplete = true,
        )

    @Test
    fun `prompt applies sampling options`() {
        val factory = OpenAiPromptFactory(TestToolRegistry())
        val prompt =
            factory.buildPrompt(
                ChatRequestContext(
                    messages = listOf(Message("user", "hi")),
                    parameters =
                        de.heckenmann.visualagent.agent.ModelParameters(
                            temperature = 0.5,
                            topP = 0.9,
                            maxTokens = 100,
                        ),
                    options = mapOf("seed" to "42", "reasoningEffort" to "low", "verbosity" to "high"),
                ),
                "gpt-test",
            )
        val options = prompt.options as org.springframework.ai.openai.OpenAiChatOptions

        assertEquals(0.5, options.temperature)
        assertEquals(0.9, options.topP)
        assertEquals(100, options.maxCompletionTokens)
        assertEquals(42, options.seed)
        assertEquals("low", options.reasoningEffort)
        assertEquals("high", options.verbosity)
    }

    private class FakeTool(
        id: String,
    ) : TestVisualAgentTool {
        override val definition =
            ToolDefinition(
                id = ToolId(id),
                name = ToolId(id).toTestFunctionName(),
                description = "Fake $id",
                inputSchema = """{"type":"object"}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = ToolResult(definition.id.value, true, "ok")
    }
}
