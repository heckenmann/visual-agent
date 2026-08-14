package de.heckenmann.visualagent.agent.tools.api

import java.time.Instant

/** Sub-agent lifecycle and inspection operations needed by agent tools. */
interface AgentToolPort {
    /** Lists sub-agents. */
    fun list(): List<ToolAgent>

    /** Reads scheduler counts. */
    fun queue(): ToolAgentQueue

    /** Finds a sub-agent. */
    fun get(id: String): ToolAgent?

    /** Creates a sub-agent. */
    fun create(
        name: String,
        role: String,
        templateName: String,
    ): ToolAgent

    /** Updates a sub-agent. */
    fun update(
        id: String,
        name: String?,
        role: String?,
        config: ToolAgentConfig,
    ): Boolean

    /** Deletes a sub-agent. */
    fun delete(id: String): Boolean

    /** Resolves enabled tool identifiers. */
    fun tools(id: String): List<String>

    /** Resolves the matching configuration identifier. */
    fun configId(id: String): String?

    /** Reads a configuration description. */
    fun configDescription(configId: String): String

    /** Reads recent work-log entries. */
    fun logs(
        id: String,
        limit: Int,
    ): List<ToolAgentLog>

    /** Creates a configuration from a template. */
    fun template(templateName: String): ToolAgentConfig

    /** Returns or changes the global/per-agent execution gates. */
    fun control(
        action: String,
        agentId: String?,
    ): ToolAgentExecutionStatus
}

/** Tool-owned sub-agent projection. */
data class ToolAgent(
    val id: String,
    val name: String,
    val role: String,
    val status: String,
    val currentTask: String?,
    val currentTodoId: String?,
    val config: ToolAgentConfig,
)

/** Tool-owned agent configuration. */
data class ToolAgentConfig(
    val timeout: Int = 60,
    val maxRetries: Int = 3,
    val memoryLimitMb: Long = 512,
    val provider: String? = null,
    val model: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val variant: String? = null,
    val options: Map<String, String> = emptyMap(),
    val tools: List<String>? = null,
    val templateName: String? = null,
)

/** Current sub-agent scheduler counts. */
data class ToolAgentQueue(
    val active: Int,
    val queued: Int,
)

/** Tool-facing projection of the shared sub-agent execution gates. */
data class ToolAgentExecutionStatus(
    val agentId: String?,
    val globalState: String,
    val agentState: String?,
    val effectiveState: String,
    val pauseReason: String,
    val pausedAgentIds: List<String>,
)

/** One persisted sub-agent work-log entry. */
data class ToolAgentLog(
    val createdAt: Instant,
    val content: String,
)
