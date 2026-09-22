package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolRegistryAsyncExecutionTest {
    @Test
    fun `tool call can run asynchronously`() {
        val events = CopyOnWriteArrayList<ToolCallEvent>()
        val finished = CountDownLatch(1)
        val bus = ToolEventBus()
        bus.addListener { event ->
            events += event
            if (event.phase == ToolCallPhase.FINISHED) finished.countDown()
        }
        val release = CountDownLatch(1)
        val registry = ToolRegistry(listOf(SlowTool("context", release)), bus) { 60 }

        val result = registry.executeBlocking(registry.resolve(setOf(ToolId("context"))).single(), """{"async":true}""", emptyMap())
        val json = Json.parseToJsonElement(result).jsonObject
        assertTrue(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(json["data"]!!.jsonPrimitive.content.contains("scheduled async"))

        release.countDown()
        finished.await()
        assertEquals(2, events.size)
        assertEquals(ToolCallPhase.STARTED, events[0].phase)
        assertEquals(ToolCallPhase.FINISHED, events[1].phase)
        assertTrue(events[1].result.success)
    }

    @Test
    fun `managed tool handles async input itself`() {
        val events = CopyOnWriteArrayList<ToolCallEvent>()
        val finished = CountDownLatch(1)
        val bus = ToolEventBus()
        bus.addListener { event ->
            events += event
            if (event.phase == ToolCallPhase.FINISHED) finished.countDown()
        }
        val registry = ToolRegistry(listOf(ManagedTool("agent:start")), bus)

        val result = registry.executeBlocking(registry.resolve(setOf(ToolId("agent:start"))).single(), """{"async":true}""", emptyMap())
        val json = Json.parseToJsonElement(result).jsonObject

        assertTrue(json["data"]!!.jsonPrimitive.content.contains("scheduled async"))
        finished.await()
        assertEquals(2, events.size)
        assertEquals(true, events.last().context["async"])
    }

    private class SlowTool(
        id: String,
        private val release: CountDownLatch,
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
            release.await()
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
