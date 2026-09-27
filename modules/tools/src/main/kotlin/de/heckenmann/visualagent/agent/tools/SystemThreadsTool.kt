package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Provides bounded diagnostics for threads in the Visual Agent server JVM. */
@AgentTool
class SystemThreadsTool(
    private val probe: ThreadDiagnosticsProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect platform threads in the Visual Agent server JVM; virtual threads are not included. " +
                    "Thread dumps include names, states, lock owners, and bounded stack frames only. " +
                    "Input: {\"action\":\"summary|deadlocks|dump\",\"state\":\"RUNNABLE\",\"maxThreads\":50,\"maxFrames\":12}.",
            inputSchema =
                """{"type":"object","properties":{"action":{"type":"string","enum":["summary","deadlocks","dump"]},"state":{"type":"string","enum":["NEW","RUNNABLE","BLOCKED","WAITING","TIMED_WAITING","TERMINATED"]},"maxThreads":{"type":"integer","minimum":1,"maximum":100},"maxFrames":{"type":"integer","minimum":1,"maximum":32}},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val action = input.string("action")?.lowercase() ?: DEFAULT_ACTION
        if (action !in ACTIONS) return failure(TOOL_ID.value, "Invalid action. Use summary, deadlocks, or dump.")
        val state = input.string("state")?.uppercase()
        if (state != null && state !in THREAD_STATES) return failure(TOOL_ID.value, "Invalid thread state filter.")
        val maxThreads = input.int("maxThreads") ?: DEFAULT_MAX_THREADS
        if (maxThreads !in MIN_THREADS..MAX_THREADS) return failure(TOOL_ID.value, "maxThreads must be between 1 and $MAX_THREADS.")
        val maxFrames = input.int("maxFrames") ?: DEFAULT_MAX_FRAMES
        if (maxFrames !in MIN_FRAMES..MAX_FRAMES) return failure(TOOL_ID.value, "maxFrames must be between 1 and $MAX_FRAMES.")
        val result =
            runCatching { probe.inspect(ThreadDiagnosticsRequest(action, state, maxThreads, maxFrames)) }
                .getOrElse { return failure(TOOL_ID.value, "JVM thread diagnostics are unavailable.") }
        val data = result.toJson()
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("system:threads")
        val ACTIONS = setOf("summary", "deadlocks", "dump")
        val THREAD_STATES =
            Thread.State.entries
                .map { it.name }
                .toSet()
        const val DEFAULT_ACTION = "summary"
        const val DEFAULT_MAX_THREADS = 50
        const val MAX_THREADS = 100
        const val MIN_THREADS = 1
        const val DEFAULT_MAX_FRAMES = 12
        const val MAX_FRAMES = 32
        const val MIN_FRAMES = 1
    }
}

/** Input bounds for one JVM thread diagnostic operation. */
data class ThreadDiagnosticsRequest(
    /** Requested operation: summary, deadlocks, or dump. */
    val action: String,
    /** Optional JVM thread-state filter for dump output. */
    val state: String?,
    /** Maximum returned thread entries. */
    val maxThreads: Int,
    /** Maximum stack frames returned per thread. */
    val maxFrames: Int,
)

/** Bounded thread diagnostics suitable for model context. */
data class ThreadDiagnosticsResult(
    /** Operation that produced this result. */
    val action: String,
    /** Current JVM thread totals and counts by state. */
    val summary: ThreadSummary,
    /** Bounded thread details for dump/deadlocks actions. */
    val threads: List<ThreadEntry>,
    /** Number of matching threads observed before result truncation. */
    val matchingThreads: Int,
    /** Whether the details were truncated. */
    val truncated: Boolean,
)

/** Aggregate platform-thread counts for the current Visual Agent JVM; virtual threads are excluded. */
data class ThreadSummary(
    /** Current live thread count. */
    val liveThreads: Int,
    /** Current daemon thread count. */
    val daemonThreads: Int,
    /** Peak live thread count since JVM start. */
    val peakThreads: Int,
    /** Current counts indexed by [Thread.State] name. */
    val states: Map<String, Int>,
    /** Number of threads participating in detected monitor or ownable-synchronizer deadlocks. */
    val deadlockedThreads: Int?,
)

/** Safe details for one JVM thread without thread-local or monitor-object contents. */
data class ThreadEntry(
    /** JVM thread ID. */
    val id: Long,
    /** Bounded thread name. */
    val name: String,
    /** Current JVM state. */
    val state: String,
    /** Lock identity string when the JVM exposes one. */
    val lock: String?,
    /** Name of the lock-owning thread, when available. */
    val lockOwner: String?,
    /** Bounded stack frames. */
    val stack: List<String>,
)

/** Reads thread state from the current Visual Agent JVM. */
fun interface ThreadDiagnosticsProbe {
    /** Return bounded details for [request]. */
    fun inspect(request: ThreadDiagnosticsRequest): ThreadDiagnosticsResult
}

private fun ThreadDiagnosticsResult.toJson() =
    buildJsonObject {
        put("action", action)
        put("runtimeScope", "visual-agent-server-jvm")
        put("threadScope", "platform-only")
        put("matchingThreads", matchingThreads)
        put("truncated", truncated)
        putJsonObject("summary") {
            put("liveThreads", summary.liveThreads)
            put("daemonThreads", summary.daemonThreads)
            put("peakThreads", summary.peakThreads)
            summary.deadlockedThreads?.let { put("deadlockedThreads", it) }
            putJsonObject("states") { summary.states.toSortedMap().forEach { (state, count) -> put(state, count) } }
        }
        putJsonArray("threads") {
            threads.forEach { thread ->
                add(
                    buildJsonObject {
                        put("id", thread.id)
                        put("name", thread.name)
                        put("state", thread.state)
                        thread.lock?.let { put("lock", it) }
                        thread.lockOwner?.let { put("lockOwner", it) }
                        putJsonArray("stack") { thread.stack.forEach { add(JsonPrimitive(it)) } }
                    },
                )
            }
        }
    }
