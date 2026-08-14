package de.heckenmann.visualagent.agent

/** Returns the persisted global and per-agent execution state. */
fun AgentManager.getSubAgentExecutionSnapshot(): SubAgentExecutionSnapshot = subAgentExecutionControl.snapshot()

/** Returns the effective execution state for an optional sub-agent. */
fun AgentManager.getSubAgentExecutionStatus(agentId: String? = null): SubAgentExecutionStatus = subAgentExecutionControl.status(agentId)

/** Pauses all sub-agent execution while preserving individual pause flags. */
fun AgentManager.pauseAllSubAgents(): SubAgentExecutionSnapshot = subAgentExecutionControl.pauseAll()

/** Resumes global sub-agent execution while preserving individual pause flags. */
fun AgentManager.resumeAllSubAgents(): SubAgentExecutionSnapshot = subAgentExecutionControl.resumeAll()

/** Pauses execution for one existing sub-agent. */
fun AgentManager.pauseSubAgent(agentId: String): SubAgentExecutionSnapshot {
    require(getSubAgent(agentId) != null) { "Agent not found: $agentId" }
    return subAgentExecutionControl.pauseAgent(agentId)
}

/** Resumes execution for one existing sub-agent. */
fun AgentManager.resumeSubAgent(agentId: String): SubAgentExecutionSnapshot {
    require(getSubAgent(agentId) != null) { "Agent not found: $agentId" }
    return subAgentExecutionControl.resumeAgent(agentId)
}

/** Registers a listener for immediate pause/resume state refreshes. */
fun AgentManager.addSubAgentExecutionListener(listener: (SubAgentExecutionSnapshot) -> Unit): AutoCloseable =
    subAgentExecutionControl.addListener(listener)
