package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Requests and returns the desktop-client process inventory only when explicitly called. */
@AgentTool
class SystemClientProcessesTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Ask the desktop client to inspect every process visible to its OS account, including PID and full command line. " +
                    "This is client data, not the server process list. Values are returned without filtering or redaction. " +
                    "Input: {\"action\":\"list|show\",\"offset\":0,\"pageSize\":50,\"pid\":1234}.",
            inputSchema =
                """{"type":"object","properties":{"action":{"type":"string","enum":["list","show"]},"offset":{"type":"integer","minimum":0},"pageSize":{"type":"integer","minimum":1,"maximum":100},"pid":{"type":"integer","minimum":0}},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val action = input.string("action")?.lowercase() ?: LIST_ACTION
        if (action !in ACTIONS) return failure(TOOL_ID.value, "Invalid action. Use list or show.")
        val offset = input.int("offset") ?: DEFAULT_OFFSET
        if (offset < 0) return failure(TOOL_ID.value, "offset must be non-negative.")
        val pageSize = input.int("pageSize") ?: DEFAULT_PAGE_SIZE
        if (pageSize !in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            return failure(TOOL_ID.value, "pageSize must be between $MIN_PAGE_SIZE and $MAX_PAGE_SIZE.")
        }
        val pid = input["pid"]?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() }
        if (action == SHOW_ACTION && (pid == null || pid < 0)) return failure(TOOL_ID.value, "show requires a non-negative pid.")
        if (action == LIST_ACTION && input["pid"] != null) return failure(TOOL_ID.value, "list does not accept pid.")
        val requester =
            context[ClientDataRequester.METADATA_KEY] as? ClientDataRequester
                ?: return failure(TOOL_ID.value, "The server has no request-scoped access to client diagnostics.")
        val request = ProcessInventoryRequest(action, offset, pageSize, pid)
        val report =
            requester.requestProcessInventoryReport(request)
                ?: return failure(TOOL_ID.value, "The client could not provide its process inventory when requested.")
        if (report.processes.any { it.pid < 0 } || report.totalProcesses < report.processes.size) {
            return failure(
                TOOL_ID.value,
                "Client process inventory contains an invalid process identifier.",
            )
        }
        val expectedOffset = if (action == LIST_ACTION) offset else 0
        if (report.offset != expectedOffset) {
            return failure(TOOL_ID.value, "Client process inventory returned a different page than requested.")
        }
        val selected = report.processes.sortedBy { it.pid }
        if (selected.distinctBy { it.pid }.size != selected.size ||
            selected.size > if (action == SHOW_ACTION) 1 else pageSize
        ) {
            return failure(TOOL_ID.value, "Client process inventory exceeded the requested page size.")
        }
        if (action == SHOW_ACTION && selected.none { it.pid == pid }) {
            return failure(TOOL_ID.value, "No client process exists for that pid.")
        }
        val snapshot =
            ProcessInventorySnapshot(
                action = action,
                offset = report.offset,
                totalProcesses = report.totalProcesses,
                processes = selected,
                hasMore = action == LIST_ACTION && report.hasMore,
                hostRole = CLIENT_HOST_ROLE,
            )
        val data = snapshot.toJson()
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("system:client-processes")
        val ACTIONS = setOf("list", "show")
        const val LIST_ACTION = "list"
        const val SHOW_ACTION = "show"
        const val DEFAULT_OFFSET = 0
        const val DEFAULT_PAGE_SIZE = 50
        const val MIN_PAGE_SIZE = 1
        const val MAX_PAGE_SIZE = 100
        const val CLIENT_HOST_ROLE = "client"
    }
}
