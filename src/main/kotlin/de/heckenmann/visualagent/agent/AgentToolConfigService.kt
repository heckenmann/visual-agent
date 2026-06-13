package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.SubAgentConfigStore
import org.springframework.stereotype.Service

/**
 * Represents AgentToolConfigService.
 */
@Service
class AgentToolConfigService(
    private val configStore: SubAgentConfigStore,
) {
    init {
        ensureDefaultConfigs()
    }

    /**
     * Return enabled tools for the main orchestration agent.
     *
     * The main agent is restricted to sub-agent control tools only.
     *
     * @return Set of tool IDs exposed to the main agent
     */
    fun mainAgentTools(): Set<ToolId> =
        setOf(
            "agent:list",
            "agent:start",
            "agent:create",
            "agent:update",
            "agent:delete",
            "agent:message",
            "agent:assign-todo",
            "agent:assign-next-todo",
            "agent:assign-all-todos",
        ).map(::ToolId).toSet()

    /**
     * Return enabled tools for a sub-agent.
     *
     * @param agent Sub-agent requesting tools
     * @return Tool IDs configured for the agent role/name
     */
    fun toolsFor(agent: SubAgent): Set<ToolId> {
        val key =
            when {
                agent.name.contains("coder", ignoreCase = true) -> "coder"
                agent.role.contains("code", ignoreCase = true) -> "coder"
                agent.name.contains("analyst", ignoreCase = true) -> "analyst"
                agent.role.contains("review", ignoreCase = true) -> "analyst"
                else -> "researcher"
            }
        val configured = configStore.getSubAgentConfig(key)?.tools ?: defaultConfigs().first { it.id == key }.tools
        return configured.map(::ToolId).toSet()
    }

    /**
     * Persist a sub-agent tool configuration.
     *
     * @param config Configuration to save
     */
    fun save(config: SubAgentToolConfig) {
        configStore.saveSubAgentConfig(config)
    }

    private fun ensureDefaultConfigs() {
        defaultConfigs().forEach { config ->
            if (configStore.getSubAgentConfig(config.id) == null) {
                save(config)
            }
        }
    }

    private fun defaultConfigs(): List<SubAgentToolConfig> =
        listOf(
            SubAgentToolConfig(
                id = "researcher",
                name = "Researcher",
                description = "Search, read, and analyze code, files, and documentation.",
                tools =
                    listOf(
                        "file:read",
                        "file:list",
                        "file:glob",
                        "file:grep",
                        "browser",
                        "search",
                        "context",
                        "pwd",
                        "todos",
                        "history",
                        "manual",
                        "sleep",
                    ),
            ),
            SubAgentToolConfig(
                id = "coder",
                name = "Coder",
                description = "Implement code changes, write new functions, fix bugs, and modify files.",
                tools =
                    listOf(
                        "file:read",
                        "file:write",
                        "file:edit",
                        "terminal",
                        "file:list",
                        "file:glob",
                        "file:grep",
                        "context",
                        "pwd",
                        "todos",
                        "history",
                        "manual",
                        "sleep",
                    ),
                maxTurns = 8,
            ),
            SubAgentToolConfig(
                id = "analyst",
                name = "Analyst",
                description = "Deep analysis, review, and explanation of code and architecture.",
                tools = listOf("file:read", "file:list", "file:glob", "file:grep", "context", "pwd", "todos", "history", "manual", "sleep"),
            ),
        )
}

/**
 * Persisted tool configuration for one agent template.
 *
 * @property id Stable config ID
 * @property name Display name
 * @property description Purpose shown in the UI or logs
 * @property model Preferred model for this agent
 * @property systemPrompt Agent-specific system prompt
 * @property tools Tool IDs allowed for this agent
 * @property maxTurns Maximum autonomous loop turns
 * @property enabled Whether this config can be used
 */
data class SubAgentToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val model: String = "nemotron-3-super:cloud",
    val systemPrompt: String = "",
    val tools: List<String>,
    val maxTurns: Int = 5,
    val enabled: Boolean = true,
)
