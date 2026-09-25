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

/** Releases the agent currently assigned to a todo, if one is still assigned. */
internal fun releaseAutonomousTodoAgentForId(
    todoId: String,
    agents: Map<String, SubAgent>,
    agentBusySince: MutableMap<String, Long>,
    subAgentOps: SubAgentOpsProvider,
) {
    agents.values.firstOrNull { it.currentTodoId == todoId }?.let { agent ->
        releaseAutonomousTodoAgent(agent, todoId, agentBusySince, subAgentOps)
    }
}

/** Cancels a todo and immediately releases its assigned agent after a successful transition. */
internal fun cancelTodoAndReleaseAgent(
    todoId: String,
    cancelTodo: (String) -> Boolean,
    agents: Map<String, SubAgent>,
    agentBusySince: MutableMap<String, Long>,
    subAgentOps: SubAgentOpsProvider,
): Boolean {
    if (!cancelTodo(todoId)) return false
    releaseAutonomousTodoAgentForId(todoId, agents, agentBusySince, subAgentOps)
    return true
}
