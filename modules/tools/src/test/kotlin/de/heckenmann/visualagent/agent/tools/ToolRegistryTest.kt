package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolRegistryTest {
    private var timeoutSeconds = 60

    @Test
    fun `registry returns only enabled registered tools`() {
        val registry = registry(FakeTool("file:read"), FakeTool("terminal"))

        val callbacks = registry.resolve(setOf(ToolId("file:read"), ToolId("missing")))

        assertEquals(listOf("file_read"), callbacks.map { it.definition.name })
    }

    @Test
    fun `todos tool is exposed only under canonical function name`() {
        val registry = registry(FakeTool("todos"))

        val callbackNames = registry.resolve(setOf(ToolId("todos"))).map { it.definition.name }.sorted()

        assertEquals(listOf("todos"), callbackNames)
    }

    @Test
    fun `function callback returns structured tool result`() {
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        bus.addListener { events += it }
        val registry = ToolRegistry(listOf(FakeTool("context")), bus) { timeoutSeconds }

        val result =
            registry.execute(
                registry.resolve(setOf(ToolId("context"))).single(),
                """{"x":1}""",
                mapOf(
                    "providerToolCallId" to "call-9",
                    "requestId" to "request-3",
                    "toolCallRound" to 1,
                    "toolCallSequence" to 2,
                ),
            )
        val json = Json.parseToJsonElement(result).jsonObject

        assertEquals("context", json["toolId"]!!.jsonPrimitive.content)
        assertTrue(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(2, events.size)
        assertEquals(ToolCallPhase.STARTED, events[0].phase)
        assertEquals(ToolCallPhase.FINISHED, events[1].phase)
        assertEquals("context", events[1].toolId)
        assertEquals("call-9", events[1].providerToolCallId)
        assertEquals("request-3", events[1].requestId)
        assertEquals(1, events[1].round)
        assertEquals(2, events[1].sequence)
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
        val registry = ToolRegistry(listOf(FailingTool("context")), bus) { timeoutSeconds }

        val result = registry.execute(registry.resolve(setOf(ToolId("context"))).single(), """{"x":1}""", emptyMap())
        val json = Json.parseToJsonElement(result).jsonObject

        assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(2, events.size)
        assertEquals(ToolCallPhase.STARTED, events[0].phase)
        assertEquals(ToolCallPhase.FINISHED, events[1].phase)
        assertFalse(events[1].result.success)
        assertEquals("boom", events[1].result.error)
    }

    @Test
    fun `tool lifecycle events redact credentials without changing execution input`() {
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        bus.addListener { events += it }
        var receivedInput = ""
        val tool =
            object : VisualAgentTool {
                override val definition =
                    ToolDefinition(
                        id = ToolId("workspace:download"),
                        name = "workspace_download",
                        description = "Fake download",
                        inputSchema = "{}",
                    )

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult {
                    receivedInput = inputJson
                    return ToolResult(definition.id.value, true, "ok")
                }
            }
        val registry = ToolRegistry(listOf(tool), bus) { timeoutSeconds }
        val input =
            """{"source":"sftp://alice:super-secret@example.org/file.txt","password":"super-secret","token":"abc"}"""

        registry.execute(tool, input, emptyMap())

        assertEquals(input, receivedInput)
        assertEquals(2, events.size)
        events.forEach { event ->
            assertFalse(event.inputJson.contains("super-secret"))
            assertFalse(event.inputJson.contains("abc"))
            assertTrue(event.inputJson.contains("[redacted]"))
        }
    }

    @Test
    fun `tool call uses default timeout when not overridden`() {
        val previousTimeout = timeoutSeconds
        timeoutSeconds = 1
        try {
            val registry = registry(SlowTool("context", 1500))
            val result = registry.execute(registry.resolve(setOf(ToolId("context"))).single(), """{}""", emptyMap())
            val json = Json.parseToJsonElement(result).jsonObject
            assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
            assertTrue(json["error"]!!.jsonPrimitive.content.contains("TOOL_TIMEOUT"))
        } finally {
            timeoutSeconds = previousTimeout
        }
    }

    @Test
    fun `tool call timeout can be overridden by model input`() {
        val previousTimeout = timeoutSeconds
        timeoutSeconds = 1
        try {
            val registry = registry(SlowTool("context", 1200))
            val result = registry.execute(registry.resolve(setOf(ToolId("context"))).single(), """{"timeoutSeconds":2}""", emptyMap())
            val json = Json.parseToJsonElement(result).jsonObject
            assertTrue(json["success"]!!.jsonPrimitive.content.toBoolean())
            assertEquals("ok", json["content"]!!.jsonPrimitive.content)
        } finally {
            timeoutSeconds = previousTimeout
        }
    }

    @Test
    fun `tool call rejects invalid timeout override without clamping`() {
        val registry = registry(FakeTool("context"))

        val result = registry.execute(registry.resolve(setOf(ToolId("context"))).single(), """{"timeoutSeconds":601}""", emptyMap())
        val json = Json.parseToJsonElement(result).jsonObject

        assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertContains(json["error"]!!.jsonPrimitive.content, "TOOL_ARGUMENTS")
        assertContains(json["error"]!!.jsonPrimitive.content, "1 and 600")
    }

    @Test
    fun `provider definitions centrally expose runtime parameters`() {
        val registry = registry(FakeTool("context"))

        val schema = Json.parseToJsonElement(registry.toolDefinitions().single().inputSchema).jsonObject
        val properties = schema["properties"]!!.jsonObject

        assertEquals("integer", properties["timeoutSeconds"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("boolean", properties["async"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tool call can run asynchronously`() {
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        bus.addListener { events += it }
        val registry = ToolRegistry(listOf(SlowTool("context", 200)), bus) { timeoutSeconds }

        val result = registry.execute(registry.resolve(setOf(ToolId("context"))).single(), """{"async":true}""", emptyMap())
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

        val result = registry.execute(registry.resolve(setOf(ToolId("agent:start"))).single(), """{"async":true}""", emptyMap())
        val json = Json.parseToJsonElement(result).jsonObject

        assertTrue(json["content"]!!.jsonPrimitive.content.contains("scheduled async"))
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline && events.count { it.phase == ToolCallPhase.FINISHED } == 0) {
            TimeUnit.MILLISECONDS.sleep(25)
        }
        assertEquals(2, events.size)
        assertEquals(true, events.last().context["async"])
    }

    private fun registry(vararg tools: VisualAgentTool) = ToolRegistry(tools.toList(), ToolEventBus()) { timeoutSeconds }

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
