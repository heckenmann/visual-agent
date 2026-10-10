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
    private val preferenceStore: PreferenceStore? = configStore as? PreferenceStore,
) {
    init {
        ensureDefaultConfigs()
    }

    /**
     * Return enabled tools for the main orchestration agent.
     *
     * The main agent can inspect and manage sub-agents, assign work, and explicitly
     * start or stop todo execution. It does not message sub-agents directly.
     *
     * @return Set of tool IDs exposed to the main agent
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun mainAgentTools(): Set<ToolId> =
        setOf(
            "agent:list",
            "agent:show",
            "agent:create",
            "agent:update",
            "agent:delete",
            "agent:log",
            "subagents:execution",
            "todos",
            "workspace:file",
            "workspace:download",
            "update:check",
            "context",
            "model:selection",
            "system:client-runtime",
            "system:processes",
            "system:client-processes",
            "system:env",
            "diagnostics:config",
            "diagnostics:database",
            "diagnostics:provider",
            "diagnostics:health",
            "diagnostics:connectors",
            "diagnostics:logs",
            SERVER_TLS_TRUST_TOOL_ID,
            SERVER_TLS_KEY_TOOL_ID,
            "javascript:execute",
            "memory",
            "skills",
            TOOL_HELP_ID,
            "tools:batch",
        ).let(::filterEnabledTools).map(::ToolId).toSet()

    /**
     * Return enabled tools for a sub-agent.
     *
     * The agent's own tool override takes precedence, followed by the persisted
     * template configuration, then a fallback to the default config for the
     * agent's stored template name.
     *
     * @param agent Sub-agent requesting tools
     * @return Tool IDs configured for the agent
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun toolsFor(agent: SubAgent): Set<ToolId> {
        agent.config.tools?.let { configured ->
            return filterSubAgentTools(configured + listOf(TOOL_HELP_ID, "tools:batch")).map(::ToolId).toSet()
        }
        val key = resolveTemplateName(agent)
        val configured = configStore.getSubAgentConfig(key)?.tools ?: defaultConfigs().firstOrNull { it.id == key }?.tools
        return filterSubAgentTools((configured ?: emptyList()) + listOf(TOOL_HELP_ID, "tools:batch")).map(::ToolId).toSet()
    }

    private fun resolveTemplateName(agent: SubAgent): String {
        val template = agent.config.templateName?.ifBlank { null }
        if (template != null) return template
        val tools = agent.config.tools
        return defaultConfigs()
            .firstOrNull { cfg ->
                tools != null && tools.toSet() == cfg.tools.toSet()
            }?.id ?: "researcher"
    }

    /**
     * Returns whether a tool is globally enabled.
     *
     * @param toolId Canonical tool ID
     * @return true when the tool is not globally disabled
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun isToolGloballyEnabled(toolId: String): Boolean =
        toolId !in RESTRICTED_HOST_ACCESS_TOOL_IDS &&
            toolId !in disabledToolIds() &&
            (toolId !in PROCESS_INVENTORY_TOOL_IDS || preferenceStore?.getPreference(PROCESS_INVENTORY_INITIALIZED_KEY) == "true") &&
            (toolId != SYSTEM_ENV_TOOL_ID || preferenceStore?.getPreference(SYSTEM_ENV_INITIALIZED_KEY) == "true")

    /**
     * Returns the persisted tool configuration id for the given sub-agent.
     *
     * The value is read from the agent's stored template name. If no template name
     * is stored, the id of the default config matching the agent's explicit tool
     * list is returned, otherwise null.
     *
     * @param agent Sub-agent to look up
     * @return Matching config id, or null when no match exists
     */
    fun findConfigIdFor(agent: SubAgent): String? {
        agent.config.templateName
            ?.ifBlank { null }
            ?.let { return it }
        agent.config.tools?.let { tools ->
            return defaultConfigs().firstOrNull { it.tools.toSet() == tools.toSet() }?.id
        }
        return null
    }

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
        require(!enabled || toolId !in RESTRICTED_HOST_ACCESS_TOOL_IDS) {
            "Unsandboxed host-access tools are permanently disabled; use workspace:file instead"
        }
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
        if (preferenceStore?.getPreference(SECURITY_TOOLS_INITIALIZED_KEY) != "true") {
            preferenceStore?.setPreference(
                DISABLED_TOOLS_KEY,
                (disabledToolIds() + SERVER_TLS_TOOL_IDS).sorted().joinToString("\n"),
            )
            preferenceStore?.setPreference(SECURITY_TOOLS_INITIALIZED_KEY, "true")
        }
        if (preferenceStore?.getPreference(PROCESS_INVENTORY_INITIALIZED_KEY) != "true") {
            preferenceStore?.setPreference(
                DISABLED_TOOLS_KEY,
                (disabledToolIds() + PROCESS_INVENTORY_TOOL_IDS).sorted().joinToString("\n"),
            )
            preferenceStore?.setPreference(PROCESS_INVENTORY_INITIALIZED_KEY, "true")
        }
        if (preferenceStore?.getPreference(SYSTEM_ENV_INITIALIZED_KEY) != "true") {
            preferenceStore?.setPreference(DISABLED_TOOLS_KEY, (disabledToolIds() + SYSTEM_ENV_TOOL_ID).sorted().joinToString("\n"))
            preferenceStore?.setPreference(SYSTEM_ENV_INITIALIZED_KEY, "true")
        }
        if (preferenceStore?.getPreference(LOG_DIAGNOSTICS_INITIALIZED_KEY) != "true") {
            preferenceStore?.setPreference(DISABLED_TOOLS_KEY, (disabledToolIds() + LOG_DIAGNOSTICS_TOOL_ID).sorted().joinToString("\n"))
            preferenceStore?.setPreference(LOG_DIAGNOSTICS_INITIALIZED_KEY, "true")
        }
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

    private fun filterSubAgentTools(tools: Collection<String>): List<String> =
        filterEnabledTools(tools).filterNot { it in MAIN_AGENT_ONLY_TOOL_IDS }

    /**
     * Returns the human-readable description for a default config id.
     *
     * @param configId Config id such as `coder`, `analyst`, or `researcher`
     * @return Description text, or empty when unknown
     */
    fun descriptionForConfigId(configId: String): String = defaultConfigs().firstOrNull { it.id == configId }?.description.orEmpty()

    /**
     * Returns the default sub-agent tool configurations.
     *
     * @return List of default [SubAgentToolConfig] instances
     */
    fun defaultConfigs(): List<SubAgentToolConfig> =
        listOf(
            SubAgentToolConfig(
                id = "researcher",
                name = "Researcher",
                description = "Search, read, and analyze code, files, and documentation.",
                tools =
                    listOf(
                        "browser",
                        "search",
                        "context",
                        "system:time",
                        "network:dns",
                        "network:reverse-dns",
                        "network:tcp",
                        "network:ping",
                        "network:traceroute",
                        "network:interfaces",
                        "network:routes",
                        "network:http",
                        "network:tls",
                        "system:threads",
                        "system:gc",
                        "system:process",
                        "system:filesystem",
                        "todos",
                        "history",
                        "manual",
                        "usecases",
                        "sleep",
                        "workspace:layout",
                        "workspace:file",
                        "workspace:download",
                        "canvas",
                        "javascript:execute",
                    ),
            ),
            SubAgentToolConfig(
                id = "coder",
                name = "Coder",
                description = "Implement code changes, write new functions, fix bugs, and modify files.",
                tools =
                    listOf(
                        "context",
                        "todos",
                        "history",
                        "manual",
                        "usecases",
                        "sleep",
                        "workspace:layout",
                        "workspace:file",
                        "workspace:download",
                        "canvas",
                        "javascript:execute",
                    ),
                maxTurns = 8,
            ),
            SubAgentToolConfig(
                id = "analyst",
                name = "Analyst",
                description = "Deep analysis, review, and explanation of code and architecture.",
                tools =
                    listOf(
                        "context",
                        "system:time",
                        "network:dns",
                        "network:reverse-dns",
                        "network:tcp",
                        "network:ping",
                        "network:traceroute",
                        "network:interfaces",
                        "network:routes",
                        "network:http",
                        "network:tls",
                        "system:threads",
                        "system:gc",
                        "system:process",
                        "system:filesystem",
                        "todos",
                        "history",
                        "manual",
                        "usecases",
                        "sleep",
                        "workspace:layout",
                        "workspace:file",
                        "workspace:download",
                        "canvas",
                        "javascript:execute",
                    ),
            ),
        )
}

private const val DISABLED_TOOLS_KEY = "tools.disabled.global"
private const val SECURITY_TOOLS_INITIALIZED_KEY = "tools.security-management.defaults.v1"
private const val PROCESS_INVENTORY_INITIALIZED_KEY = "tools.process-inventory.defaults.v1"
private const val SYSTEM_ENV_INITIALIZED_KEY = "tools.system-env.defaults.v1"
private const val LOG_DIAGNOSTICS_INITIALIZED_KEY = "tools.log-diagnostics.defaults.v1"
private const val TOOL_HELP_ID = "tool:help"
private const val SYSTEM_ENV_TOOL_ID = "system:env"
private const val LOG_DIAGNOSTICS_TOOL_ID = "diagnostics:logs"
private const val SERVER_TLS_TRUST_TOOL_ID = "security:truststore"
private const val SERVER_TLS_KEY_TOOL_ID = "security:keystore"

private val SERVER_TLS_TOOL_IDS = setOf(SERVER_TLS_TRUST_TOOL_ID, SERVER_TLS_KEY_TOOL_ID)
private val PROCESS_INVENTORY_TOOL_IDS = setOf("system:processes", "system:client-processes")
private val MAIN_AGENT_ONLY_TOOL_IDS =
    SERVER_TLS_TOOL_IDS +
        setOf(
            "diagnostics:config",
            "diagnostics:provider",
            "diagnostics:health",
            "diagnostics:connectors",
            LOG_DIAGNOSTICS_TOOL_ID,
            "system:client-runtime",
            "system:client-processes",
        )

private val RESTRICTED_HOST_ACCESS_TOOL_IDS =
    setOf(
        "file:read",
        "file:list",
        "file:glob",
        "file:grep",
        "file:write",
        "file:edit",
        "terminal",
        "pwd",
    )

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
