package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Reports bounded garbage-collector and memory-pool metrics for the server JVM. */
@AgentTool
class SystemGcTool(
    private val probe: GcDiagnosticsProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect garbage-collection counters and bounded memory-pool usage for the Visual Agent server JVM. " +
                    "Input: {}. Counts and times may be unavailable when the JVM does not expose them.",
            inputSchema = EMPTY_OBJECT_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        parseObject(inputJson)
        val snapshot =
            runCatching { probe.snapshot() }
                .getOrElse { return failure(TOOL_ID.value, "JVM garbage-collection diagnostics are unavailable.") }
        val data = snapshot.toJson()
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("system:gc")
        const val EMPTY_OBJECT_SCHEMA = """{"type":"object","properties":{},"additionalProperties":false}"""
    }
}

/** One collector's monotonic totals since the JVM started. */
data class GarbageCollectorMetric(
    /** Collector name reported by the JVM. */
    val name: String,
    /** Total collection count, or null when unsupported. */
    val collectionCount: Long?,
    /** Total collection time in milliseconds, or null when unsupported. */
    val collectionTimeMillis: Long?,
)

/** Current usage of one JVM memory pool. */
data class MemoryPoolMetric(
    /** JVM-provided pool name. */
    val name: String,
    /** Heap or non-heap pool category. */
    val type: String,
    /** Current used bytes, if available. */
    val usedBytes: Long?,
    /** Currently committed bytes, if available. */
    val committedBytes: Long?,
    /** Maximum bytes, if the JVM defines a maximum. */
    val maxBytes: Long?,
)

/** Bounded aggregate of collector totals and JVM memory pools. */
data class GcDiagnosticsSnapshot(
    /** Garbage collectors observed. */
    val collectors: List<GarbageCollectorMetric>,
    /** Memory pools observed, capped by the JVM probe. */
    val memoryPools: List<MemoryPoolMetric>,
    /** Whether either source list was truncated. */
    val truncated: Boolean,
)

/** Supplies a deterministic snapshot of JVM garbage collection and memory pools. */
fun interface GcDiagnosticsProbe {
    /** Read one bounded snapshot. */
    fun snapshot(): GcDiagnosticsSnapshot
}

private fun GcDiagnosticsSnapshot.toJson() =
    buildJsonObject {
        put("runtimeScope", "visual-agent-server-jvm")
        put("truncated", truncated)
        putJsonArray("collectors") {
            collectors.forEach { collector ->
                add(
                    buildJsonObject {
                        put("name", collector.name)
                        collector.collectionCount?.let { put("collectionCount", it) }
                        collector.collectionTimeMillis?.let { put("collectionTimeMillis", it) }
                    },
                )
            }
        }
        putJsonArray("memoryPools") {
            memoryPools.forEach { pool ->
                add(
                    buildJsonObject {
                        put("name", pool.name)
                        put("type", pool.type)
                        pool.usedBytes?.let { put("usedBytes", it) }
                        pool.committedBytes?.let { put("committedBytes", it) }
                        pool.maxBytes?.let { put("maxBytes", it) }
                    },
                )
            }
        }
    }
