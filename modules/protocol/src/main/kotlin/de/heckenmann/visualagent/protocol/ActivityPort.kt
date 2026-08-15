package de.heckenmann.visualagent.protocol

/** Application activity events translated for the presentation layer. */
interface ActivityPort {
    /** Registers a listener for tool activity changes. */
    fun addToolListener(listener: (ToolActivity) -> Unit): AutoCloseable

    /** Registers a listener for sub-agent lifecycle changes. */
    fun addAgentListener(listener: (AgentActivity) -> Unit): AutoCloseable
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
