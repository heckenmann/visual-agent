package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentJobResult
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent

/** Executes one ad-hoc sub-agent job while maintaining the agent's persisted lifecycle state. */
internal suspend fun AgentManager.executeSubAgentJob(
    agent: SubAgent,
    content: String,
): AgentJobResult {
    activeJobsByAgentId.compute(agent.id) { _, count -> (count ?: 0) + 1 }
    agent.status = AgentStatus.BUSY
    agent.currentTask = content
    agent.currentTodoId = null
    saveSubAgent(agent)
    agentStatusCallbackAdapter.notify(agent.id, "STATUS:${agent.status.name}")
    return try {
        val response =
            agent.chat(
                messages = listOf(Message("user", content)),
                provider = llmProvider,
                enabledTools = agentToolConfigService.toolsFor(agent),
            )
        AgentJobResult(agent.id, agent.name, response.message.content)
    } finally {
        val remainingJobs =
            activeJobsByAgentId.compute(agent.id) { _, count ->
                val remaining = (count ?: 1) - 1
                remaining.takeIf { it > 0 }
            } ?: 0
        agent.status = if (remainingJobs > 0) AgentStatus.BUSY else AgentStatus.IDLE
        if (remainingJobs == 0) agent.currentTask = null
        agent.currentTodoId = null
        saveSubAgent(agent)
        agentStatusCallbackAdapter.notify(agent.id, "STATUS:${agent.status.name}")
        if (remainingJobs == 0) autonomousCoordinator.signalWork()
    }
}
