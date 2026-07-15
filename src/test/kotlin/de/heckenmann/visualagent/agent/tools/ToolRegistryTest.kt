package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.config.AppConfigBean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolRegistryTest {
    private val appConfig = AppConfigBean()

    @Test
    fun `registry returns only enabled registered tools`() {
        val registry = ToolRegistry(listOf(FakeTool("file:read"), FakeTool("terminal")), ToolEventBus(), appConfig)

        val callbacks = registry.functionCallbacks(setOf(ToolId("file:read"), ToolId("missing")))

        assertEquals(listOf("file_read"), callbacks.map { it.toolDefinition.name() })
    }

    @Test
    fun `todos tool is exposed only under canonical function name`() {
        val registry = ToolRegistry(listOf(FakeTool("todos")), ToolEventBus(), AppConfigBean())

        val callbackNames = registry.functionCallbacks(setOf(ToolId("todos"))).map { it.toolDefinition.name() }.sorted()

        assertEquals(listOf("todos"), callbackNames)
    }

    @Test
    fun `function callback returns structured tool result`() {
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        bus.addListener { events += it }
        val registry = ToolRegistry(listOf(FakeTool("context")), bus, appConfig)

        val result = registry.functionCallbacks(setOf(ToolId("context"))).single().call("""{"x":1}""")
        val json = Json.parseToJsonElement(result).jsonObject

        assertEquals("context", json["toolId"]!!.jsonPrimitive.content)
        assertTrue(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(2, events.size)
        assertEquals(ToolCallPhase.STARTED, events[0].phase)
        assertEquals(ToolCallPhase.FINISHED, events[1].phase)
        assertEquals("context", events[1].toolId)
        assertTrue(events[1].result.success)
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
        val registry = ToolRegistry(listOf(FailingTool("context")), bus, appConfig)

        val result = registry.functionCallbacks(setOf(ToolId("context"))).single().call("""{"x":1}""")
        val json = Json.parseToJsonElement(result).jsonObject

        assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(2, events.size)
        assertEquals(ToolCallPhase.STARTED, events[0].phase)
        assertEquals(ToolCallPhase.FINISHED, events[1].phase)
        assertFalse(events[1].result.success)
        assertEquals("boom", events[1].result.error)
    }

    @Test
    fun `tool call uses default timeout when not overridden`() {
        val previousTimeout = appConfig.timeoutSeconds
        appConfig.timeoutSeconds = 1
        try {
            val registry = ToolRegistry(listOf(SlowTool("context", 1500)), ToolEventBus(), appConfig)
            val result = registry.functionCallbacks(setOf(ToolId("context"))).single().call("""{}""")
            val json = Json.parseToJsonElement(result).jsonObject
            assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
            assertTrue(json["error"]!!.jsonPrimitive.content.contains("timed out"))
        } finally {
            appConfig.timeoutSeconds = previousTimeout
        }
    }

    @Test
    fun `tool call timeout can be overridden by model input`() {
        val previousTimeout = appConfig.timeoutSeconds
        appConfig.timeoutSeconds = 1
        try {
            val registry = ToolRegistry(listOf(SlowTool("context", 1200)), ToolEventBus(), appConfig)
            val result = registry.functionCallbacks(setOf(ToolId("context"))).single().call("""{"timeoutSeconds":2}""")
            val json = Json.parseToJsonElement(result).jsonObject
            assertTrue(json["success"]!!.jsonPrimitive.content.toBoolean())
            assertEquals("ok", json["content"]!!.jsonPrimitive.content)
        } finally {
            appConfig.timeoutSeconds = previousTimeout
        }
    }

    @Test
    fun `tool call can run asynchronously`() {
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        bus.addListener { events += it }
        val registry = ToolRegistry(listOf(SlowTool("context", 200)), bus, appConfig)

        val result = registry.functionCallbacks(setOf(ToolId("context"))).single().call("""{"async":true}""")
        val json = Json.parseToJsonElement(result).jsonObject
        assertTrue(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(json["content"]!!.jsonPrimitive.content.contains("scheduled async"))

        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline && events.count { it.phase == ToolCallPhase.FINISHED } == 0) {
            TimeUnit.MILLISECONDS.sleep(25)
        }
        assertEquals(2, events.size)
        assertEquals(ToolCallPhase.STARTED, events[0].phase)
        assertEquals(ToolCallPhase.FINISHED, events[1].phase)
        assertTrue(events[1].result.success)
    }

    @Test
    fun `managed tool handles async input itself`() {
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        bus.addListener { events += it }
        val registry = ToolRegistry(listOf(ManagedTool("agent:start")), bus)

        val result = registry.functionCallbacks(setOf(ToolId("agent:start"))).single().call("""{"async":true}""")
        val json = Json.parseToJsonElement(result).jsonObject

        assertEquals("managed", json["content"]!!.jsonPrimitive.content)
        assertEquals(2, events.size)
        assertEquals(true, events.last().context["managedExecution"])
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

    private class FailingTool(
        id: String,
    ) : VisualAgentTool {
        override val definition =
            ToolDefinition(
                id = ToolId(id),
                name = ToolId(id).toFunctionName(),
                description = "Failing $id",
                inputSchema = """{"type":"object"}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = throw IllegalStateException("boom")
    }

    private class SlowTool(
        id: String,
        private val delayMillis: Long,
    ) : VisualAgentTool {
        override val definition =
            ToolDefinition(
                id = ToolId(id),
                name = ToolId(id).toFunctionName(),
                description = "Slow $id",
                inputSchema = """{"type":"object"}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult {
            TimeUnit.MILLISECONDS.sleep(delayMillis)
            return ToolResult(definition.id.value, true, "ok")
        }
    }

    private class ManagedTool(
        id: String,
    ) : VisualAgentTool {
        override val managesExecution: Boolean = true
        override val definition =
            ToolDefinition(
                id = ToolId(id),
                name = ToolId(id).toFunctionName(),
                description = "Managed $id",
                inputSchema = """{"type":"object"}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = ToolResult(definition.id.value, true, "managed")
    }
}
