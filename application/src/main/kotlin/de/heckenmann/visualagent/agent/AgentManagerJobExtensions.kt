package de.heckenmann.visualagent.agent

/** Sends a message to a sub-agent after waiting for its execution gate. */
internal suspend fun AgentManager.sendMessageToAgent(
    agentId: String,
    content: String,
): String {
    val requestId = conversationOps.beginConversationRequest()
    subAgentExecutionControl.awaitExecutionAllowed(agentId)
    return conversationOps.sendMessageToAgent(agentId, content, requestId)
}

/** Runs a sub-agent job synchronously. */
internal suspend fun AgentManager.runAgentJob(
    agentId: String,
    content: String,
): AgentJobResult {
    val requestId = conversationOps.beginConversationRequest()
    return subAgentJobScheduler.run(agentId) {
        conversationOps.runAgentJob(agentId, content, requestId)
    }
}

/** Enqueues a job for an existing sub-agent. */
internal fun AgentManager.enqueueAgentJob(
    agentId: String,
    content: String,
): String =
    enqueueConversationJob(
        agentId = agentId,
        block = { requestId -> conversationOps.runAgentJob(agentId, content, requestId) },
    )

/** Runs a temporary sub-agent job synchronously. */
internal suspend fun AgentManager.startAgentJob(
    name: String,
    role: String,
    templateName: String,
    content: String,
): AgentJobResult {
    val requestId = conversationOps.beginConversationRequest()
    return subAgentJobScheduler.run {
        conversationOps.startAgentJob(name, role, templateName, content, requestId)
    }
}

/** Enqueues a temporary sub-agent job. */
internal fun AgentManager.enqueueAgentJob(
    name: String,
    role: String,
    templateName: String,
    content: String,
): String =
    enqueueConversationJob(
        agentId = null,
        block = { requestId -> conversationOps.startAgentJob(name, role, templateName, content, requestId) },
    )

private fun AgentManager.enqueueConversationJob(
    agentId: String?,
    block: suspend (String) -> AgentJobResult,
): String {
    val requestId = conversationOps.beginConversationRequest()
    return subAgentJobScheduler.enqueue(
        agentId = agentId,
        block = { block(requestId) },
        onFinished = { jobId, result -> conversationOps.notifyMainAgentOfJobCompletion(jobId, result, requestId) },
    )
}

/** Cancels a queued or running sub-agent job. */
internal fun AgentManager.cancelSubAgentJob(jobId: String): Boolean = subAgentJobScheduler.cancelJob(jobId)

/** Returns the number of active jobs for an agent. */
internal fun AgentManager.getActiveJobCount(agentId: String): Int = activeJobsByAgentId[agentId] ?: 0
