package de.heckenmann.visualagent.agent

/** Sends a message to a sub-agent after waiting for its execution gate. */
internal suspend fun AgentManager.sendMessageToAgent(
    agentId: String,
    content: String,
): String {
    subAgentExecutionControl.awaitExecutionAllowed(agentId)
    return conversationOps.sendMessageToAgent(agentId, content)
}

/** Runs a sub-agent job synchronously. */
internal suspend fun AgentManager.runAgentJob(
    agentId: String,
    content: String,
): AgentJobResult =
    subAgentJobScheduler.run(agentId) {
        conversationOps.runAgentJob(agentId, content)
    }

/** Enqueues a job for an existing sub-agent. */
internal fun AgentManager.enqueueAgentJob(
    agentId: String,
    content: String,
): String =
    subAgentJobScheduler.enqueue(
        agentId = agentId,
        block = { conversationOps.runAgentJob(agentId, content) },
        onFinished = conversationOps::notifyMainAgentOfJobCompletion,
    )

/** Runs a temporary sub-agent job synchronously. */
internal suspend fun AgentManager.startAgentJob(
    name: String,
    role: String,
    templateName: String,
    content: String,
): AgentJobResult =
    subAgentJobScheduler.run {
        conversationOps.startAgentJob(name, role, templateName, content)
    }

/** Enqueues a temporary sub-agent job. */
internal fun AgentManager.enqueueAgentJob(
    name: String,
    role: String,
    templateName: String,
    content: String,
): String =
    subAgentJobScheduler.enqueue(
        block = { conversationOps.startAgentJob(name, role, templateName, content) },
        onFinished = conversationOps::notifyMainAgentOfJobCompletion,
    )

/** Cancels a queued or running sub-agent job. */
internal fun AgentManager.cancelSubAgentJob(jobId: String): Boolean = subAgentJobScheduler.cancelJob(jobId)

/** Returns the number of active jobs for an agent. */
internal fun AgentManager.getActiveJobCount(agentId: String): Int = activeJobsByAgentId[agentId] ?: 0
