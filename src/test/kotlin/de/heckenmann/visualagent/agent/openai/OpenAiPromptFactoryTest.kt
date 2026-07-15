package de.heckenmann.visualagent.agent.openai

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
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.model.tool.ToolCallingChatOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiPromptFactoryTest {
    @Test
    fun `prompt attaches enabled tool callbacks`() {
        val registry = ToolRegistry(listOf(FakeTool("context"), FakeTool("terminal")), ToolEventBus())
        val factory = OpenAiPromptFactory(registry)

        val prompt =
            factory.buildPrompt(
                ChatRequestContext(
                    messages = listOf(Message("user", "show context")),
                    enabledTools = setOf(ToolId("context")),
                ),
                "gpt-test",
            )
        val options = prompt.options as ToolCallingChatOptions

        assertEquals("gpt-test", prompt.options?.model)
        assertEquals(listOf("context"), options.toolCallbacks.orEmpty().map { it.toolDefinition.name() })
        assertTrue(
            prompt.instructions
                .first()
                .text
                .orEmpty()
                .contains("Tool calling strict mode"),
        )
        assertEquals(
            "openai",
            options.toolContext.orEmpty()["provider"].toString(),
        )
    }

    @Test
    fun `prompt omits guard when no tools enabled`() {
        val factory = OpenAiPromptFactory(ToolRegistry(emptyList(), ToolEventBus(), AppConfigBean(mockk(relaxed = true))))

        val prompt = factory.buildPrompt(ChatRequestContext(messages = listOf(Message("user", "hi"))), "gpt-test")

        assertTrue(prompt.instructions.none { it.text.orEmpty().contains("Tool calling strict mode") })
    }

    @Test
    fun `allowedFunctionNames returns sorted enabled names`() {
        val registry = ToolRegistry(listOf(FakeTool("terminal"), FakeTool("context")), ToolEventBus())
        val factory = OpenAiPromptFactory(registry)

        val names =
            factory.allowedFunctionNames(
                ChatRequestContext(
                    messages = emptyList(),
                    enabledTools = setOf(ToolId("terminal"), ToolId("context")),
                ),
                "gpt-test",
            )

        assertEquals(listOf("context", "terminal"), names)
    }

    @Test
    fun `prompt applies sampling options`() {
        val factory = OpenAiPromptFactory(ToolRegistry(emptyList(), ToolEventBus(), AppConfigBean(mockk(relaxed = true))))
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
