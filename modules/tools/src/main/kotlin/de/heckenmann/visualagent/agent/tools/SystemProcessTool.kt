package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Reports safe resource metrics for the current Visual Agent server process only. */
@AgentTool
class SystemProcessTool(
    private val probe: ServerProcessMetricsProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Report PID, start time, CPU time/load, JVM uptime, and JVM memory for the current Visual Agent server process. " +
                    "Does not inspect other processes, command lines, or environment values. Input: {}.",
            inputSchema = EMPTY_OBJECT_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        parseObject(inputJson)
        val snapshot =
            runCatching { probe.snapshot() }
                .getOrElse { return failure(TOOL_ID.value, "Current server process diagnostics are unavailable.") }
        val data = snapshot.toJson()
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("system:process")
        const val EMPTY_OBJECT_SCHEMA = """{"type":"object","properties":{},"additionalProperties":false}"""
    }
}

/** Safe point-in-time metrics for the current server process and its JVM. */
data class ServerProcessMetrics(
    /** Operating-system process identifier. */
    val processId: Long,
    /** Process start epoch time in milliseconds, when available. */
    val startedAtEpochMillis: Long?,
    /** Current process CPU time in milliseconds, when available. */
    val cpuTimeMillis: Long?,
    /** Current process CPU load from zero to one, when available. */
    val cpuLoad: Double?,
    /** JVM uptime in milliseconds. */
    val jvmUptimeMillis: Long,
    /** Heap currently used by the process JVM in bytes. */
    val heapUsedBytes: Long,
    /** Heap committed to the process JVM in bytes. */
    val heapCommittedBytes: Long,
    /** Heap maximum in bytes, or null when not defined. */
    val heapMaxBytes: Long?,
)

/** Supplies current-process metrics without exposing command-line or environment data. */
fun interface ServerProcessMetricsProbe {
    /** Read one snapshot for the current server process. */
    fun snapshot(): ServerProcessMetrics
}

private fun ServerProcessMetrics.toJson() =
    buildJsonObject {
        put("runtimeScope", "visual-agent-server-process")
        put("processId", processId)
        startedAtEpochMillis?.let { put("startedAtEpochMillis", it) }
        cpuTimeMillis?.let { put("cpuTimeMillis", it) }
        cpuLoad?.let { put("cpuLoad", it) }
        put("jvmUptimeMillis", jvmUptimeMillis)
        put("heapUsedBytes", heapUsedBytes)
        put("heapCommittedBytes", heapCommittedBytes)
        heapMaxBytes?.let { put("heapMaxBytes", it) }
    }
