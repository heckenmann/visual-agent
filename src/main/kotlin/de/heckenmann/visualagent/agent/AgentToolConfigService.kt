package de.heckenmann.visualagent.agent.config

import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.knowledge.SubAgentConfigStore
import org.springframework.stereotype.Service

/**
 * Resolves the tool set exposed to the main agent and each sub-agent role.
 *
 * Use cases: UC-0000019, UC-0000020, UC-0000033, UC-0000036, UC-0000067.
 */
@Service
class AgentToolConfigService(
    private val configStore: SubAgentConfigStore,
) {
    private val preferenceStore = configStore as? PreferenceStore

    init {
        ensureDefaultConfigs()
    }

    /**
     * Return enabled tools for the main orchestration agent.
     *
     * The main agent can manage sub-agent definitions but cannot start, message, or assign
     * work to sub-agents directly. Assignment and execution happen automatically through
     * the autonomous coordinator.
     *
     * @return Set of tool IDs exposed to the main agent
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun mainAgentTools(): Set<ToolId> =
        setOf(
            "agent:list",
            "agent:create",
            "agent:update",
            "agent:delete",
            "agent:log",
        ).let(::filterEnabledTools).map(::ToolId).toSet()

    /**
     * Return enabled tools for a sub-agent.
     *
     * @param agent Sub-agent requesting tools
     * @return Tool IDs configured for the agent role/name
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun toolsFor(agent: SubAgent): Set<ToolId> {
        agent.config.tools?.let { configured ->
            return filterEnabledTools(configured).map(::ToolId).toSet()
        }
        val key =
            when {
                agent.name.contains("coder", ignoreCase = true) -> "coder"
                agent.role.contains("code", ignoreCase = true) -> "coder"
                agent.name.contains("analyst", ignoreCase = true) -> "analyst"
                agent.role.contains("review", ignoreCase = true) -> "analyst"
                else -> "researcher"
            }
        val configured = configStore.getSubAgentConfig(key)?.tools ?: defaultConfigs().first { it.id == key }.tools
        return filterEnabledTools(configured).map(::ToolId).toSet()
    }

    /**
     * Returns whether a tool is globally enabled.
     *
     * @param toolId Canonical tool ID
     * @return true when the tool is not globally disabled
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun isToolGloballyEnabled(toolId: String): Boolean = toolId !in disabledToolIds()

    /**
     * Enables or disables one tool globally.
     *
     * Disabled tools are filtered from main-agent and sub-agent tool sets.
     *
     * @param toolId Canonical tool ID
     * @param enabled Whether the tool should be exposed to model requests
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun setToolGloballyEnabled(
        toolId: String,
        enabled: Boolean,
    ) {
        val next =
            if (enabled) {
                disabledToolIds() - toolId
            } else {
                disabledToolIds() + toolId
            }
        preferenceStore?.setPreference(DISABLED_TOOLS_KEY, next.sorted().joinToString("\n"))
    }

    /**
     * Returns globally disabled tool IDs.
     *
     * Use cases: UC-0000019.
     */
    fun disabledToolIds(): Set<String> =
        preferenceStore
            ?.getPreference(DISABLED_TOOLS_KEY)
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

    /**
     * Persist a sub-agent tool configuration.
     *
     * @param config Configuration to save
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun save(config: SubAgentToolConfig) {
        configStore.saveSubAgentConfig(config)
    }

    private fun ensureDefaultConfigs() {
        defaultConfigs().forEach { config ->
            val existing = configStore.getSubAgentConfig(config.id)
            if (existing == null) {
                save(config)
            } else {
                val mergedTools = (existing.tools + config.tools.filter { it !in existing.tools }).distinct()
                if (mergedTools != existing.tools) {
                    save(existing.copy(tools = mergedTools))
                }
            }
        }
    }

    private fun filterEnabledTools(tools: Collection<String>): List<String> = tools.filter(::isToolGloballyEnabled)

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
                        "usecases",
                        "sleep",
                        "workspace:layout",
                        "workspace:file",
                        "canvas",
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
                        "usecases",
                        "sleep",
                        "workspace:layout",
                        "workspace:file",
                        "canvas",
                    ),
                maxTurns = 8,
            ),
            SubAgentToolConfig(
                id = "analyst",
                name = "Analyst",
                description = "Deep analysis, review, and explanation of code and architecture.",
                tools =
                    listOf(
                        "file:read",
                        "file:list",
                        "file:glob",
                        "file:grep",
                        "context",
                        "pwd",
                        "todos",
                        "history",
                        "manual",
                        "usecases",
                        "sleep",
                        "workspace:layout",
                        "workspace:file",
                        "canvas",
                    ),
            ),
        )
}

private const val DISABLED_TOOLS_KEY = "tools.disabled.global"

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
 * @see docs/usecases/uc_0000019_configure_agent_tools.md
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
