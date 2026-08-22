package de.heckenmann.visualagent.protocol

/** Application activity events translated for the presentation layer. */
interface ActivityPort {
    /** Registers a listener for tool activity changes. */
    fun addToolListener(listener: (ToolActivity) -> Unit): AutoCloseable

    /** Registers a listener for sub-agent lifecycle changes. */
    fun addAgentListener(listener: (AgentActivity) -> Unit): AutoCloseable

    /** Registers a listener for server-owned workspace download status changes. */
    fun addDownloadListener(listener: (DownloadActivity) -> Unit): AutoCloseable = AutoCloseable {}
}

/** One tool execution transition. */
data class ToolActivity(
    val toolId: String,
    val requestId: String? = null,
    val phase: ToolActivityPhase,
    val success: Boolean = true,
)

/** Tool execution phase. */
enum class ToolActivityPhase {
    STARTED,
    FINISHED,
}

/** One sub-agent lifecycle transition. */
data class AgentActivity(
    val agentId: String,
    val phase: AgentActivityPhase,
)

/** Sub-agent lifecycle phase. */
enum class AgentActivityPhase {
    STARTED,
    FINISHED,
}

/** A server-owned workspace download status transition. */
data class DownloadActivity(
    val id: String,
    val relativePath: String,
    val status: DownloadActivityStatus,
    val downloadedBytes: Long,
    val totalBytes: Long? = null,
    val detail: String? = null,
)

/** Lifecycle statuses reported for a workspace download. */
enum class DownloadActivityStatus {
    STARTED,
    PAUSED,
    RESUMED,
    COMPLETED,
    CANCELLED,
    FAILED,
}
