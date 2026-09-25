package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import de.heckenmann.visualagent.agent.ToolId as ProviderToolId

/** Verifies provider callback execution through the registry's function-name contract. */
class SpringAiToolCallbacksAdapterTest {
    @Test
    fun `does not inject globally disabled tool help into provider callbacks`() {
        val regularTool =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("workspace:file"), "workspace_file", "Reads files", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult = ToolResult(definition.id.value, true, "read")
            }
        val helpTool = ToolHelpTool { error("Tool help must not execute in this test") }
        val adapter = SpringAiToolCallbacksAdapter(ToolRegistry(listOf(regularTool, helpTool), ToolEventBus()))

        val callbacks = adapter.functionCallbacks(setOf(ProviderToolId("workspace:file")))

        assertEquals(listOf("workspace_file"), callbacks.map { it.toolDefinition.name() })
    }

    @Test
    fun `structured agent list callback executes the matching internal tool`() {
        var executions = 0
        val tool =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("agent:list"), "agent_list", "Lists agents", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult {
                    executions++
                    return ToolResult(definition.id.value, true, "listed")
                }
            }
        val callbacks =
            SpringAiToolCallbacksAdapter(ToolRegistry(listOf(tool), ToolEventBus()))
                .functionCallbacks(setOf(ProviderToolId("agent:list")))

        val callback = callbacks.single()
        val result = Json.parseToJsonElement(callback.call("{}")).jsonObject

        assertEquals("agent_list", callback.toolDefinition.name())
        assertEquals("agent:list", result["toolId"]!!.jsonPrimitive.content)
        assertEquals(1, executions)
    }
}
