package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies request-scoped tool discovery and delegated execution. */
class ToolHelpToolTest {
    @Test
    fun `list and show include only tools enabled for this request`() {
        val registry = ToolRegistry(listOf(FakeTool("workspace:file"), FakeTool("terminal")), ToolEventBus())
        val help = ToolHelpTool { registry }
        val context = mapOf("enabledTools" to setOf("workspace:file"))

        val list = help.execute("""{"action":"list"}""", context)
        val details = help.execute("""{"action":"show","name":"workspace_file"}""", context)
        val denied = help.execute("""{"action":"show","name":"terminal"}""", context)

        assertTrue(list.content.contains("workspace_file"))
        assertFalse(list.content.contains("terminal"))
        assertTrue(details.content.contains("Input schema"))
        assertFalse(denied.success)
    }

    @Test
    fun `delegated calls execute only an enabled provider function`() {
        val allowed = FakeTool("workspace:file")
        val registry = ToolRegistry(listOf(allowed, FakeTool("terminal")), ToolEventBus())
        val help = ToolHelpTool { registry }
        val context = mapOf("enabledTools" to setOf("workspace:file"))

        val result =
            help.execute(
                """{"action":"call","name":"workspace_file","arguments":{"value":"hello"}}""",
                context,
            )
        val denied =
            help.execute(
                """{"action":"call","name":"terminal","arguments":{}}""",
                context,
            )

        assertTrue(result.success)
        assertTrue(result.content.contains("executed:hello"))
        assertTrue(allowed.called)
        assertFalse(denied.success)
    }

    private class FakeTool(
        id: String,
    ) : VisualAgentTool {
        var called = false

        override val definition =
            ToolDefinition(
                id = ToolId(id),
                name = ToolId(id).toFunctionName(),
                description = "Accepts a value",
                inputSchema = """{"type":"object","properties":{"value":{"type":"string"}}}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult {
            called = true
            return success(toolId = definition.id.value, content = "executed:${parseObject(inputJson).string("value")}")
        }
    }
}
