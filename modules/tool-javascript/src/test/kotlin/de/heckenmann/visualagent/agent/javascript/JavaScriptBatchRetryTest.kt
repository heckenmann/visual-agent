package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.api.ToolBatchSafety
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.failure
import de.heckenmann.visualagent.agent.tools.success
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/** Covers result reservations after rejected guest Promises. */
class JavaScriptBatchRetryTest {
    @Test
    fun `caught argument failure leaves room for corrected retry`() {
        val starts = AtomicInteger()
        val registry = ToolRegistry(listOf(testTool("read", starts)), ToolEventBus())
        for (isolate in listOf(false, true)) {
            starts.set(0)
            GraalJavaScriptExecutionService(
                { registry },
                JavaScriptWorkspaceWriter {
                    _,
                    _,
                    ->
                    error("Unused")
                },
                preferIsolate = isolate,
            ).use {
                val result =
                    it.execute(
                        JavaScriptExecutionRequest(
                            "try { await tools.callMany([{id:'bad',name:'read',arguments:{bad:true}}]); } catch(e) {} return (await tools.callMany([{id:'ok',name:'read',arguments:{}}]))[0].success;",
                            setOf("read"),
                            limits = JavaScriptExecutionLimits(maxResultCharacters = 65536),
                        ),
                    )
                assertEquals(true, result.value)
                assertEquals(2, starts.get())
            }
        }
    }

    private fun testTool(
        id: String,
        starts: AtomicInteger,
    ): VisualAgentTool =
        object : VisualAgentTool {
            override val definition = ToolDefinition(ToolId(id), id.replace(':', '_'), "Test", "{}", ToolBatchSafety.READ_ONLY_PARALLEL)

            override fun execute(
                inputJson: String,
                context: Map<String, Any>,
            ): ToolResult {
                starts.incrementAndGet()
                return if (inputJson.contains("bad")) failure(id, "TOOL_ARGUMENTS: invalid argument") else success(id, "done")
            }
        }
}
