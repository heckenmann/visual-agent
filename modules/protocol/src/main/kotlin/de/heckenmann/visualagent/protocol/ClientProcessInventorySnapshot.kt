package de.heckenmann.visualagent.protocol

/** One bounded, point-in-time page from the explicitly requested client process inventory. */
data class ClientProcessInventorySnapshot(
    /** Zero-based offset used for a list request, or zero for a show request. */
    val offset: Int,
    /** Number of process identifiers visible when the client collected the page. */
    val totalProcesses: Int,
    /** Process entries returned for the request. */
    val processes: List<ClientProcessEntry>,
    /** Whether more entries follow this page. */
    val hasMore: Boolean,
)

/** Bounded server request for a client process inventory page or one process. */
data class ClientProcessInventoryRequest(
    /** Either `list` or `show`. */
    val action: String,
    /** Zero-based offset for `list`; ignored for `show`. */
    val offset: Int,
    /** Maximum number of process entries requested for `list`. */
    val pageSize: Int,
    /** Exact process identifier required by `show`. */
    val pid: Long?,
) {
    init {
        require(action == LIST_ACTION || action == SHOW_ACTION) { "Unsupported client process inventory action." }
        require(offset >= 0) { "Client process inventory offset must be non-negative." }
        require(pageSize in 1..MAX_PAGE_SIZE) { "Client process inventory page size is outside its allowed range." }
        require(if (action == SHOW_ACTION) pid != null && pid >= 0 else pid == null) {
            "Client process inventory PID does not match its action."
        }
    }

    private companion object {
        const val LIST_ACTION = "list"
        const val SHOW_ACTION = "show"
        const val MAX_PAGE_SIZE = 100
    }
}

/** One client-host process entry as reported by the local operating system. */
data class ClientProcessEntry(
    /** Process identifier on the desktop client. */
    val pid: Long,
    /** Parent process identifier, if available. */
    val parentPid: Long?,
    /** Process start time in epoch milliseconds, if available. */
    val startEpochMillis: Long?,
    /** Full command line as reported by the operating system, without filtering. */
    val commandLine: String?,
    /** Executable path or name, if available. */
    val command: String?,
    /** Original argument vector, if available. */
    val arguments: List<String>?,
)

/** Captures only the requested bounded client process page for request-scoped diagnostics. */
fun interface ClientProcessInventoryDiagnosticsPort {
    /** Return the requested point-in-time page, or null when the client cannot provide it. */
    fun snapshot(request: ClientProcessInventoryRequest): ClientProcessInventorySnapshot?
}
