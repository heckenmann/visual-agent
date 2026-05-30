package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolRegistryTest {

    @Test
    fun `registry returns only enabled registered tools`() {
        val registry = ToolRegistry(listOf(FakeTool("file:read"), FakeTool("terminal")), ToolEventBus())

        val callbacks = registry.functionCallbacks(setOf(ToolId("file:read"), ToolId("missing")))

        assertEquals(listOf("file_read"), callbacks.map { it.name })
    }

    @Test
    fun `function callback returns structured tool result`() {
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        bus.addListener { events += it }
        val registry = ToolRegistry(listOf(FakeTool("context")), bus)

        val result = registry.functionCallbacks(setOf(ToolId("context"))).single().call("""{"x":1}""")
        val json = Json.parseToJsonElement(result).jsonObject

        assertEquals("context", json["toolId"]!!.jsonPrimitive.content)
        assertTrue(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(1, events.size)
        assertEquals("context", events.single().toolId)
        assertTrue(events.single().result.success)
    }

    @Test
    fun `workspace file tools reject paths outside workspace`() {
        val result = FileReadTool().execute("""{"path":"../../outside.txt"}""")

        assertFalse(result.success)
        assertEquals("file:read", result.toolId)
    }

    @Test
    fun `tool event is fired for tool execution errors`() {
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        bus.addListener { events += it }
        val registry = ToolRegistry(listOf(FailingTool("context")), bus)

        val result = registry.functionCallbacks(setOf(ToolId("context"))).single().call("""{"x":1}""")
        val json = Json.parseToJsonElement(result).jsonObject

        assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(1, events.size)
        assertFalse(events.single().result.success)
        assertEquals("boom", events.single().result.error)
    }

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

    private class FailingTool(id: String) : VisualAgentTool {
        override val definition = ToolDefinition(
            id = ToolId(id),
            name = ToolId(id).toFunctionName(),
            description = "Failing $id",
            inputSchema = """{"type":"object"}""",
        )

        override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
            throw IllegalStateException("boom")
        }
    }
}
