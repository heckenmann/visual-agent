package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Lists host process identifiers and their operating-system-reported commands without redaction. */
@AgentTool
class SystemProcessesTool(
    private val probe: HostProcessInventoryProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect every process visible to the Visual Agent server account, including PID and the full command line. " +
                    "Returned values preserve operating-system commands without redaction or truncation. " +
                    "Use list pagination for the complete inventory, filter with a command substring, or show one PID. Input: " +
                    "{\"action\":\"list|filter|show\",\"offset\":0,\"pageSize\":50,\"pid\":1234,\"query\":\"java\"}.",
            inputSchema =
                """{"type":"object","properties":{"action":{"type":"string","enum":["list","filter","show"]},"offset":{"type":"integer","minimum":0},"pageSize":{"type":"integer","minimum":1,"maximum":100},"pid":{"type":"integer","minimum":0},"query":{"type":"string","minLength":1,"maxLength":128}},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val action = input.string("action")?.lowercase() ?: LIST_ACTION
        if (action !in ACTIONS) return failure(TOOL_ID.value, "Invalid action. Use list, filter, or show.")
        val offset = input.int("offset") ?: DEFAULT_OFFSET
        if (offset < 0) return failure(TOOL_ID.value, "offset must be non-negative.")
        val pageSize = input.int("pageSize") ?: DEFAULT_PAGE_SIZE
        if (pageSize !in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            return failure(TOOL_ID.value, "pageSize must be between $MIN_PAGE_SIZE and $MAX_PAGE_SIZE.")
        }
        val pid = input["pid"]?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() }
        if (action == SHOW_ACTION && (pid == null || pid < 0)) return failure(TOOL_ID.value, "show requires a non-negative pid.")
        val query = input.string("query")
        if (action == FILTER_ACTION && (query.isNullOrBlank() || query.length > MAX_QUERY_LENGTH)) {
            return failure(TOOL_ID.value, "filter requires a query of 1 to $MAX_QUERY_LENGTH characters.")
        }
        if (action != FILTER_ACTION && query != null) return failure(TOOL_ID.value, "query is accepted only for filter.")
        if (action != SHOW_ACTION && input["pid"] != null) return failure(TOOL_ID.value, "pid is accepted only for show.")
        val request = ProcessInventoryRequest(action, offset, pageSize, pid, query)
        val snapshot =
            runCatching { probe.inspect(request) }
                .getOrElse { return failure(TOOL_ID.value, "Host process inventory is unavailable.") }
        if (action == SHOW_ACTION && snapshot.processes.isEmpty()) return failure(TOOL_ID.value, "No visible process exists for that pid.")
        val data = snapshot.toJson()
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("system:processes")
        val ACTIONS = setOf("list", "filter", "show")
        const val LIST_ACTION = "list"
        const val FILTER_ACTION = "filter"
        const val SHOW_ACTION = "show"
        const val DEFAULT_OFFSET = 0
        const val DEFAULT_PAGE_SIZE = 50
        const val MIN_PAGE_SIZE = 1
        const val MAX_PAGE_SIZE = 100
        const val MAX_QUERY_LENGTH = 128
    }
}

/** One operating-system process entry as reported by the JDK Process API. */
data class HostProcessEntry(
    /** Process identifier. */
    val pid: Long,
    /** Parent process identifier, if available. */
    val parentPid: Long?,
    /** Process start epoch milliseconds, if available. */
    val startEpochMillis: Long?,
    /** Full command line as reported by the operating system, without filtering. */
    val commandLine: String?,
    /** Executable command path/name, if available. */
    val command: String?,
    /** Original argument vector, if available. */
    val arguments: List<String>?,
)

/** Bounded result page for the complete process inventory or a single process. */
data class ProcessInventorySnapshot(
    /** Requested action. */
    val action: String,
    /** Requested offset for list, or zero for show. */
    val offset: Int,
    /** Total visible processes in this snapshot. */
    val totalProcesses: Int,
    /** Returned processes, ordered by PID for list. */
    val processes: List<HostProcessEntry>,
    /** Whether additional entries remain after this page. */
    val hasMore: Boolean,
    /** Host that produced this inventory: server or desktop client. */
    val hostRole: String = "server",
)

/** Reads the set of processes visible to the server account. */
fun interface HostProcessInventoryProbe {
    /** Inspect the requested page or process. */
    fun inspect(request: ProcessInventoryRequest): ProcessInventorySnapshot
}

/** Bounded request for process inventory diagnostics. */
data class ProcessInventoryRequest(
    /** List, filter, or show. */
    val action: String,
    /** Zero-based offset for list. */
    val offset: Int,
    /** Maximum returned entries for list. */
    val pageSize: Int,
    /** Exact process identifier for show. */
    val pid: Long?,
    /** Optional case-insensitive command substring for filter. */
    val query: String? = null,
)

internal fun ProcessInventorySnapshot.toJson() =
    buildJsonObject {
        put("runtimeScope", "visual-agent-$hostRole-host")
        put("hostRole", hostRole)
        put("action", action)
        put("offset", offset)
        put("totalProcesses", totalProcesses)
        put("hasMore", hasMore)
        put("redacted", false)
        putJsonArray("processes") {
            processes.forEach { process ->
                add(
                    buildJsonObject {
                        put("pid", process.pid)
                        process.parentPid?.let { put("parentPid", it) }
                        process.startEpochMillis?.let { put("startEpochMillis", it) }
                        process.commandLine?.let { put("commandLine", it) }
                        process.command?.let { put("command", it) }
                        process.arguments?.let { args -> putJsonArray("arguments") { args.forEach { add(JsonPrimitive(it)) } } }
                    },
                )
            }
        }
    }
