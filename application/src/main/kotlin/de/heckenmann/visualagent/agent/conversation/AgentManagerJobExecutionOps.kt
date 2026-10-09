package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentJobResult
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** Executes a native provider request, isolating synchronous agent lifecycle persistence. */
internal fun AgentManager.executeSubAgentJob(
    agent: SubAgent,
    content: String,
    requestId: String? = null,
): Mono<AgentJobResult> =
    Mono.usingWhen(
        Mono
            .fromCallable {
                activeJobsByAgentId.compute(agent.id) { _, count -> (count ?: 0) + 1 }
                agent
            }.subscribeOn(Schedulers.boundedElastic()),
        {
            Mono
                .fromRunnable<Void> {
                    agent.status = AgentStatus.BUSY
                    agent.currentTask = content
                    agent.currentTodoId = null
                    saveSubAgent(agent)
                    agentStatusCallbackAdapter.notify(agent.id, "STATUS:${agent.status.name}")
                }.subscribeOn(Schedulers.boundedElastic())
                .then(
                    Mono.defer {
                        agent
                            .chatReactive(
                                messages = listOf(Message("user", content)),
                                provider = llmProvider,
                                enabledTools = agentToolConfigService.toolsFor(agent),
                                requestId = requestId,
                            ).map { response -> AgentJobResult(agent.id, agent.name, response.message.content) }
                    },
                )
        },
        { finishSubAgentJob(it) },
        { worker, _ -> finishSubAgentJob(worker) },
        { finishSubAgentJob(it) },
    )

private fun AgentManager.finishSubAgentJob(agent: SubAgent): Mono<Void> =
    Mono
        .fromRunnable<Void> {
            val remainingJobs =
                activeJobsByAgentId.compute(agent.id) { _, count ->
                    val remaining = (count ?: 1) - 1
                    remaining.takeIf { it > 0 }
                } ?: 0
            if (subAgentOpsProvider.getSubAgent(agent.id) === agent) {
                agent.status = if (remainingJobs > 0) AgentStatus.BUSY else AgentStatus.IDLE
                if (remainingJobs == 0) agent.currentTask = null
                agent.currentTodoId = null
                saveSubAgent(agent)
                agentStatusCallbackAdapter.notify(agent.id, "STATUS:${agent.status.name}")
            }
            if (remainingJobs == 0) autonomousCoordinator.signalWork()
        }.subscribeOn(Schedulers.boundedElastic())
