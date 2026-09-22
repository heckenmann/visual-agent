package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentOpsProvider

/**
 * Releases an agent after its claimed autonomous todo is no longer in progress.
 */
internal fun releaseAutonomousTodoAgent(
    agent: SubAgent,
    todoId: String,
    agentBusySince: MutableMap<String, Long>,
    subAgentOps: SubAgentOpsProvider,
) {
    if (agent.currentTodoId != todoId) return

    agentBusySince.remove(agent.id)
    agent.status = AgentStatus.IDLE
    agent.currentTask = null
    agent.currentTodoId = null
    subAgentOps.saveSubAgent(agent)
    subAgentOps.notifyAgent(agent.id, "STATUS:${agent.status.name}")
}
