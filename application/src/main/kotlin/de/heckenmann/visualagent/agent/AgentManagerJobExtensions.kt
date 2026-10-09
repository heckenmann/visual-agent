package de.heckenmann.visualagent.agent

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import reactor.core.publisher.Mono

/** Sends a message to a sub-agent after waiting for its execution gate. */
internal suspend fun AgentManager.sendMessageToAgent(
    agentId: String,
    content: String,
): String {
    val requestId = conversationOps.beginConversationRequest()
    subAgentExecutionControl.executionAllowed(agentId).awaitSingleOrNull()
    return conversationOps.sendMessageToAgent(agentId, content, requestId)
}

/** Runs a sub-agent job synchronously. */
internal suspend fun AgentManager.runAgentJob(
    agentId: String,
    content: String,
): AgentJobResult {
    val requestId = conversationOps.beginConversationRequest()
    return subAgentJobScheduler
        .runReactive(agentId, requestId) {
            conversationOps.runAgentJobReactive(agentId, content, requestId)
        }.awaitSingle()
}

/** Enqueues a job for an existing sub-agent. */
internal fun AgentManager.enqueueAgentJob(
    agentId: String,
    content: String,
): String =
    enqueueConversationJob(
        agentId = agentId,
        block = { requestId -> conversationOps.runAgentJobReactive(agentId, content, requestId) },
    )

/** Runs a temporary sub-agent job synchronously. */
internal suspend fun AgentManager.startAgentJob(
    name: String,
    role: String,
    templateName: String,
    content: String,
): AgentJobResult {
    val requestId = conversationOps.beginConversationRequest()
    return subAgentJobScheduler
        .runReactive(null, requestId) {
            conversationOps.startAgentJobReactive(name, role, templateName, content, requestId)
        }.awaitSingle()
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
        block = { requestId -> conversationOps.startAgentJobReactive(name, role, templateName, content, requestId) },
    )

private fun AgentManager.enqueueConversationJob(
    agentId: String?,
    block: (String) -> Mono<AgentJobResult>,
): String {
    val requestId = conversationOps.beginConversationRequest()
    return subAgentJobScheduler.enqueueReactive(
        agentId = agentId,
        block = { block(requestId) },
        onFinished = { jobId, result -> conversationOps.notifyMainAgentOfJobCompletion(jobId, result, requestId) },
    )
}

/** Cancels a queued or running sub-agent job. */
internal fun AgentManager.cancelSubAgentJob(jobId: String): Boolean = subAgentJobScheduler.cancelJob(jobId)

/** Returns the number of active jobs for an agent. */
internal fun AgentManager.getActiveJobCount(agentId: String): Int = activeJobsByAgentId[agentId] ?: 0
