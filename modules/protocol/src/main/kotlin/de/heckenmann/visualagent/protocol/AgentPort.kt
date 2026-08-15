package de.heckenmann.visualagent.protocol

/** Agent lifecycle, configuration, and execution operations exposed to the UI. */
interface AgentPort {
    /** Lists all persisted sub-agents. */
    fun list(): List<Agent>

    /** Creates and persists a sub-agent from a named template. */
    fun create(
        name: String,
        role: String,
        templateName: String,
    ): Agent

    /** Updates one agent and returns the updated value, or null when absent. */
    fun update(
        id: String,
        name: String,
        role: String,
        config: AgentConfig,
    ): Agent?

    /** Deletes one agent. */
    fun delete(id: String): Boolean

    /** Returns the number of active jobs for one agent. */
    fun activeJobCount(agentId: String): Int

    /** Reads the global and individual execution gates. */
    fun executionSnapshot(): AgentExecutionSnapshot

    /** Pauses all sub-agent execution. */
    suspend fun pauseAll(): AgentExecutionSnapshot

    /** Resumes all sub-agent execution. */
    suspend fun resumeAll(): AgentExecutionSnapshot

    /** Pauses one sub-agent. */
    suspend fun pause(agentId: String): AgentExecutionSnapshot

    /** Resumes one sub-agent. */
    suspend fun resume(agentId: String): AgentExecutionSnapshot

    /** Returns the tools available for one agent configuration. */
    fun toolsFor(agentId: String): Set<String>

    /** Returns tool definitions that can be selected in the editor. */
    fun toolDefinitions(): List<ToolDefinition>

    /** Registers a listener for execution state changes. */
    fun addExecutionListener(listener: (AgentExecutionSnapshot) -> Unit): AutoCloseable

    /** Registers a listener for agent persistence changes. */
    fun addChangeListener(listener: () -> Unit): AutoCloseable
}

/** Persisted sub-agent DTO independent of application implementation types. */
data class Agent(
    val id: String,
    val name: String,
    val role: String,
    val status: AgentStatus,
    val currentTask: String? = null,
    val currentTodoId: String? = null,
    val chatHistory: List<ConversationMessage> = emptyList(),
    val config: AgentConfig = AgentConfig(),
)

/** Runtime state of one sub-agent. */
enum class AgentStatus {
    IDLE,
    BUSY,
    OFFLINE,
}

/** Runtime and model configuration for a sub-agent. */
data class AgentConfig(
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
) {
    companion object {
        /** Configurations offered by the agent creation dialog. */
        val templates: Map<String, AgentConfig> =
            mapOf(
                "researcher" to AgentConfig(timeout = 120, maxRetries = 5, memoryLimitMb = 512),
                "coder" to AgentConfig(timeout = 180, maxRetries = 3, memoryLimitMb = 1024),
                "documenter" to AgentConfig(timeout = 90, maxRetries = 2, memoryLimitMb = 256),
                "reviewer" to AgentConfig(timeout = 150, maxRetries = 3, memoryLimitMb = 768),
                "tester" to AgentConfig(timeout = 120, maxRetries = 4, memoryLimitMb = 512),
            )

        /** Creates a configuration from a named template. */
        fun fromTemplate(name: String): AgentConfig = (templates[name] ?: AgentConfig()).copy(templateName = name)
    }
}

/** Global and per-agent execution gate state. */
data class AgentExecutionSnapshot(
    val globallyPaused: Boolean,
    val pausedAgentIds: Set<String> = emptySet(),
)

/** Tool metadata displayed in the sub-agent editor. */
data class ToolDefinition(
    val id: String,
    val description: String,
)
