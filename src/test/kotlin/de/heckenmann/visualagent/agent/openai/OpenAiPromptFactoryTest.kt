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
