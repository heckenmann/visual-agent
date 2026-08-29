package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatusCallbackAdapter
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentExecutionSnapshot
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.protocol.Agent
import de.heckenmann.visualagent.protocol.AgentConfig
import de.heckenmann.visualagent.protocol.AgentExecutionSnapshot
import de.heckenmann.visualagent.protocol.AgentPort
import de.heckenmann.visualagent.protocol.AgentStatus
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ToolDefinition
import org.springframework.stereotype.Component
import de.heckenmann.visualagent.agent.AgentConfig as ApplicationAgentConfig
import de.heckenmann.visualagent.agent.AgentStatus as ApplicationAgentStatus
import de.heckenmann.visualagent.agent.Message as ApplicationMessage
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition as ApplicationToolDefinition

/** Maps agent orchestration services to the neutral [AgentPort]. */
@Component
class SpringAgentPort(
    private val agentManager: AgentManager,
    private val agentToolConfigService: AgentToolConfigService,
    private val toolRegistry: ToolRegistry,
    private val agentStatusCallbackAdapter: AgentStatusCallbackAdapter,
) : AgentPort {
    override fun list(): List<Agent> = agentManager.getSubAgents().map(SubAgent::toProtocol)

    override fun create(
        name: String,
        role: String,
        templateName: String,
    ): Agent = agentManager.createAgent(name, role, templateName).toProtocol()

    override fun update(
        id: String,
        name: String,
        role: String,
        config: AgentConfig,
    ): Agent? {
        val updated = agentManager.updateAgent(id, name, role, config.toApplication())
        return if (updated) agentManager.getSubAgent(id)?.toProtocol() else null
    }

    override fun delete(id: String): Boolean = agentManager.deleteAgent(id)

    override fun activeJobCount(agentId: String): Int = agentManager.getActiveJobCount(agentId)

    override fun executionSnapshot(): AgentExecutionSnapshot = agentManager.subAgentExecutionControl.snapshot().toProtocol()

    override suspend fun pauseAll(): AgentExecutionSnapshot = agentManager.subAgentExecutionControl.pauseAllAsync().toProtocol()

    override suspend fun resumeAll(): AgentExecutionSnapshot = agentManager.subAgentExecutionControl.resumeAllAsync().toProtocol()

    override suspend fun pause(agentId: String): AgentExecutionSnapshot =
        agentManager.subAgentExecutionControl.pauseAgentAsync(agentId).toProtocol()

    override suspend fun resume(agentId: String): AgentExecutionSnapshot =
        agentManager.subAgentExecutionControl.resumeAgentAsync(agentId).toProtocol()

    override fun toolsFor(agentId: String): Set<String> =
        agentManager
            .getSubAgent(agentId)
            ?.let { agent ->
                agentToolConfigService.toolsFor(agent).map { it.value }.toSet()
            }.orEmpty()

    override fun toolDefinitions(): List<ToolDefinition> = toolRegistry.toolDefinitions().map(ApplicationToolDefinition::toProtocol)

    override fun addExecutionListener(listener: (AgentExecutionSnapshot) -> Unit): AutoCloseable =
        agentManager.subAgentExecutionControl.addListener { listener(it.toProtocol()) }

    override fun addChangeListener(listener: () -> Unit): AutoCloseable =
        agentStatusCallbackAdapter.addListener { _, message ->
            if (message == "CREATED" || message.startsWith("STATUS:") || message == "DELETED") listener()
        }
}

private fun SubAgent.toProtocol(): Agent =
    Agent(
        id = id,
        name = name,
        role = role,
        status = status.toProtocol(),
        currentTask = currentTask,
        currentTodoId = currentTodoId,
        chatHistory = chatHistory.map(ApplicationMessage::toProtocol),
        config = config.toProtocol(),
    )

private fun ApplicationAgentStatus.toProtocol(): AgentStatus =
    when (this) {
        ApplicationAgentStatus.IDLE -> AgentStatus.IDLE
        ApplicationAgentStatus.BUSY -> AgentStatus.BUSY
        ApplicationAgentStatus.OFFLINE -> AgentStatus.OFFLINE
    }

private fun ApplicationAgentConfig.toProtocol(): AgentConfig =
    AgentConfig(
        timeout = timeout,
        maxRetries = maxRetries,
        memoryLimitMb = memoryLimitMb,
        provider = provider,
        model = model,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        variant = variant,
        options = options,
        tools = tools,
        templateName = templateName,
    )

private fun AgentConfig.toApplication(): ApplicationAgentConfig =
    ApplicationAgentConfig(
        timeout = timeout,
        maxRetries = maxRetries,
        memoryLimitMb = memoryLimitMb,
        provider = provider,
        model = model,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        variant = variant,
        options = options,
        tools = tools,
        templateName = templateName,
    )

private fun ApplicationMessage.toProtocol(): ConversationMessage =
    ConversationMessage(
        role = role,
        content = content,
        metadata = metadata,
        images = images,
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        timelineSequence = timelineSequence,
    )

private fun SubAgentExecutionSnapshot.toProtocol(): AgentExecutionSnapshot =
    AgentExecutionSnapshot(globalState.name == "PAUSED", pausedAgentIds)

private fun ApplicationToolDefinition.toProtocol(): ToolDefinition = ToolDefinition(id = id.value, description = description)
