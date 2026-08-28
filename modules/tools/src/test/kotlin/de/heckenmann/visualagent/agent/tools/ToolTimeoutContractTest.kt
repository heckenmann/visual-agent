package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ToolTimeoutContractTest {
    @Test
    fun `managed tool cannot bypass registry timeout`() {
        val registry = ToolRegistry(listOf(SlowManagedTool()), ToolEventBus()) { 1 }
        val tool = registry.resolve(setOf(ToolId("javascript:execute"))).single()

        val result = registry.execute(tool, "{}", emptyMap())
        val json = Json.parseToJsonElement(result).jsonObject

        assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertContains(json["error"]!!.jsonPrimitive.content, "TOOL_TIMEOUT")
    }

    @Test
    fun `nested call cannot exceed its inherited deadline`() {
        val registry = ToolRegistry(listOf(SlowManagedTool("context")), ToolEventBus()) { 600 }
        val tool = registry.resolve(setOf(ToolId("context"))).single()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)

        val result = registry.execute(tool, """{"timeoutSeconds":600}""", mapOf("toolDeadlineNanos" to deadline))
        val json = Json.parseToJsonElement(result).jsonObject

        assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertContains(json["error"]!!.jsonPrimitive.content, "TOOL_TIMEOUT")
    }

    @Test
    fun `outer request cancellation interrupts the tool future`() {
        val parent = ToolCancellationToken()
        val started = CountDownLatch(1)
        val result = AtomicReference<String>()
        val registry = ToolRegistry(listOf(BlockingTool(started)), ToolEventBus()) { 30 }
        val tool = registry.resolve(setOf(ToolId("context"))).single()
        val thread =
            Thread {
                result.set(
                    registry.execute(
                        tool,
                        "{}",
                        mapOf("toolCancellationRegistrar" to ToolCancellationRegistrar(parent::onCancelled)),
                    ),
                )
            }

        thread.start()
        assertFalse(started.await(5, TimeUnit.SECONDS).not())
        parent.cancel()
        thread.join(5_000)

        assertFalse(thread.isAlive)
        assertContains(result.get(), "TOOL_CANCELLED")
    }

    private class SlowManagedTool(
        id: String = "javascript:execute",
    ) : VisualAgentTool {
        override val managesExecution: Boolean = true
        override val definition =
            ToolDefinition(
                id = ToolId(id),
                name = ToolId(id).toFunctionName(),
                description = "Slow managed tool",
                inputSchema = """{"type":"object"}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult {
            TimeUnit.MILLISECONDS.sleep(1_500)
            return ToolResult(definition.id.value, true, "late")
        }
    }

    private class BlockingTool(
        private val started: CountDownLatch,
    ) : VisualAgentTool {
        override val definition =
            ToolDefinition(
                id = ToolId("context"),
                name = "context",
                description = "Blocking tool",
                inputSchema = """{"type":"object"}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult {
            started.countDown()
            Thread.sleep(30_000)
            return ToolResult(definition.id.value, true, "late")
        }
    }
}
