package de.heckenmann.visualagent.agent

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull

/** Keeps the existing gate tests on their coroutine test harness. */
internal suspend fun SubAgentExecutionControl.awaitExecutionAllowed(agentId: String? = null) {
    executionAllowed(agentId).awaitSingleOrNull()
}

internal suspend fun SubAgentExecutionControl.pauseAllAsync() = pauseAllReactive().awaitSingle()

internal suspend fun SubAgentExecutionControl.resumeAllAsync() = resumeAllReactive().awaitSingle()

internal suspend fun SubAgentExecutionControl.pauseAgentAsync(agentId: String) = pauseAgentReactive(agentId).awaitSingle()

internal suspend fun SubAgentExecutionControl.resumeAgentAsync(agentId: String) = resumeAgentReactive(agentId).awaitSingle()
