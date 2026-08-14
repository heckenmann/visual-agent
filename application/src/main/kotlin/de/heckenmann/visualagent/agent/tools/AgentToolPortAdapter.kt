package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.getSubAgentExecutionStatus
import de.heckenmann.visualagent.agent.pauseAllSubAgents
import de.heckenmann.visualagent.agent.pauseSubAgent
import de.heckenmann.visualagent.agent.resumeAllSubAgents
import de.heckenmann.visualagent.agent.resumeSubAgent
import de.heckenmann.visualagent.agent.tools.api.AgentToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolAgent
import de.heckenmann.visualagent.agent.tools.api.ToolAgentConfig
import de.heckenmann.visualagent.agent.tools.api.ToolAgentExecutionStatus
import de.heckenmann.visualagent.agent.tools.api.ToolAgentLog
import de.heckenmann.visualagent.agent.tools.api.ToolAgentQueue
import de.heckenmann.visualagent.knowledge.MemoryStore
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/** Application adapter for sub-agent operations consumed by agent tools. */
@Component
class AgentToolPortAdapter(
    @param:Lazy private val agentManager: AgentManager,
    @param:Lazy private val toolConfig: AgentToolConfigService,
    private val memoryStore: MemoryStore,
) : AgentToolPort {
    override fun list(): List<ToolAgent> = agentManager.getSubAgents().map(::toToolAgent)

    override fun queue(): ToolAgentQueue = agentManager.getSubAgentJobQueueSnapshot().let { ToolAgentQueue(it.active, it.queued) }

    override fun get(id: String): ToolAgent? = agentManager.getSubAgent(id)?.let(::toToolAgent)

    override fun create(
        name: String,
        role: String,
        templateName: String,
    ): ToolAgent = toToolAgent(agentManager.createAgent(name, role, templateName))

    override fun update(
        id: String,
        name: String?,
        role: String?,
        config: ToolAgentConfig,
    ): Boolean = agentManager.updateAgent(id, name, role, config.toApplication())

    override fun delete(id: String): Boolean = agentManager.deleteAgent(id)

    override fun tools(id: String): List<String> =
        agentManager
            .getSubAgent(id)
            ?.let(toolConfig::toolsFor)
            ?.map { it.value }
            .orEmpty()

    override fun configId(id: String): String? = agentManager.getSubAgent(id)?.let(toolConfig::findConfigIdFor)

    override fun configDescription(configId: String): String = toolConfig.descriptionForConfigId(configId)

    override fun logs(
        id: String,
        limit: Int,
    ): List<ToolAgentLog> = memoryStore.searchMemories("agent:$id:log", limit).map { ToolAgentLog(it.createdAt, it.content) }

    override fun template(templateName: String): ToolAgentConfig = AgentConfig.fromTemplate(templateName).toToolConfig()

    override fun control(
        action: String,
        agentId: String?,
    ): ToolAgentExecutionStatus {
        val normalizedAgentId = agentId?.trim()?.takeIf(String::isNotBlank)
        if (normalizedAgentId != null && agentManager.getSubAgent(normalizedAgentId) == null) {
            throw IllegalArgumentException("Agent not found: $normalizedAgentId")
        }
        when (action.trim().lowercase()) {
            "status" -> Unit
            "pause" ->
                if (normalizedAgentId == null) {
                    agentManager.pauseAllSubAgents()
                } else {
                    agentManager.pauseSubAgent(normalizedAgentId)
                }
            "resume" ->
                if (normalizedAgentId == null) {
                    agentManager.resumeAllSubAgents()
                } else {
                    agentManager.resumeSubAgent(normalizedAgentId)
                }
            "pause-all" -> agentManager.pauseAllSubAgents()
            "resume-all" -> agentManager.resumeAllSubAgents()
            else -> throw IllegalArgumentException("Unsupported execution action: $action")
        }
        val status = agentManager.getSubAgentExecutionStatus(normalizedAgentId)
        return ToolAgentExecutionStatus(
            agentId = status.agentId,
            globalState = status.globalState.name,
            agentState = status.agentState?.name,
            effectiveState = status.effectiveState.name,
            pauseReason = status.pauseReason.name,
            pausedAgentIds = status.pausedAgentIds.sorted(),
        )
    }
}

private fun toToolAgent(agent: SubAgent) =
    ToolAgent(
        id = agent.id,
        name = agent.name,
        role = agent.role,
        status = agent.status.name,
        currentTask = agent.currentTask,
        currentTodoId = agent.currentTodoId,
        config = agent.config.toToolConfig(),
    )

private fun AgentConfig.toToolConfig() =
    ToolAgentConfig(
        timeout,
        maxRetries,
        memoryLimitMb,
        provider,
        model,
        temperature,
        topP,
        maxTokens,
        variant,
        options,
        tools,
        templateName,
    )

private fun ToolAgentConfig.toApplication() =
    AgentConfig(timeout, maxRetries, memoryLimitMb, provider, model, temperature, topP, maxTokens, variant, options, tools, templateName)
