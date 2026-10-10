package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolHelpTool
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

/** Covers indirect composition restrictions in JavaScript. */
class JavaScriptDelegatedBatchTest {
    @Test
    fun `help cannot delegate a batch from JavaScript`() {
        val starts = AtomicInteger()
        val batch = testTool("tools:batch", starts)
        val children = ToolRegistry(listOf(batch), ToolEventBus())
        val help = ToolHelpTool { children }
        val registry = ToolRegistry(listOf(batch, help), ToolEventBus())
        for (isolate in listOf(false, true)) {
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
                            "return tools.call('tool_help',{action:'call',name:'tools_batch',arguments:{calls:[]}}).success;",
                            setOf("tool:help", "tools:batch"),
                            requestContext = mapOf("enabledTools" to setOf("tool:help", "tools:batch")),
                            limits = JavaScriptExecutionLimits(maxToolCalls = 1),
                        ),
                    )
                assertEquals(false, result.value)
                assertEquals(0, starts.get())
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
