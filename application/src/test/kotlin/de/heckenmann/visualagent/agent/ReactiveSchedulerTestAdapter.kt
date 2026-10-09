package de.heckenmann.visualagent.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono

/** Binds native scheduler lifetime to the existing test scope. */
internal fun SubAgentJobScheduler(
    scope: CoroutineScope,
    parallelismProvider: ParallelismProvider,
    executionControl: SubAgentExecutionControl? = null,
): SubAgentJobScheduler =
    SubAgentJobScheduler(parallelismProvider, executionControl).also { scheduler ->
        scope.coroutineContext[Job]?.invokeOnCompletion { scheduler.close() }
    }

/** Awaits the native scheduler only at the test harness boundary. */
internal suspend fun <T : Any> SubAgentJobScheduler.run(
    agentId: String? = null,
    requestId: String? = null,
    block: suspend () -> T,
): T = runReactive(agentId, requestId) { mono { block() } }.awaitSingle()

/** Supplies coroutine-controlled publishers from tests. */
internal fun <T : Any> SubAgentJobScheduler.enqueue(
    agentId: String? = null,
    block: suspend () -> T,
    onFinished: (String, Result<T>) -> Unit,
): String = enqueueReactive(agentId, { mono { block() } }, onFinished)
