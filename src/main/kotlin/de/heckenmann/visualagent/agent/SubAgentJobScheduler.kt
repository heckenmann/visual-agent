package de.heckenmann.visualagent.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Schedules sub-agent jobs against the user-configured parallelism limit.
 *
 * Waiting jobs are admitted in FIFO order. The limit is read repeatedly so queued jobs
 * can start after the user raises the configured capacity.
 *
 * @property scope Coroutine scope used for queued background jobs
 * @property parallelismProvider Current maximum number of concurrently running sub-agent jobs
 */
@Component
class SubAgentJobScheduler(
    private val scope: CoroutineScope,
    private val parallelismProvider: ParallelismProvider,
) {
    private val lock = Any()
    private val waiting = ArrayDeque<CompletableDeferred<Unit>>()
    private var activeJobs = 0
    private val jobsById = ConcurrentHashMap<String, Job>()

    init {
        scope.launch {
            while (true) {
                dispatchWaitingJobs()
                delay(DISPATCH_INTERVAL_MILLIS)
            }
        }
    }

    /**
     * Runs one job after a parallel execution slot becomes available.
     *
     * @param block Job implementation
     * @return Job result
     */
    suspend fun <T> run(block: suspend () -> T): T {
        val permit = CompletableDeferred<Unit>()
        synchronized(lock) {
            waiting.addLast(permit)
        }
        dispatchWaitingJobs()
        permit.await()
        return try {
            block()
        } finally {
            synchronized(lock) {
                activeJobs = (activeJobs - 1).coerceAtLeast(0)
            }
            dispatchWaitingJobs()
        }
    }

    /**
     * Queues a background job and returns its stable ID immediately.
     *
     * @param block Job implementation
     * @param onFinished Completion callback receiving success or failure
     * @return Queued job ID
     */
    fun <T> enqueue(
        block: suspend () -> T,
        onFinished: (jobId: String, result: Result<T>) -> Unit,
    ): String {
        val jobId = UUID.randomUUID().toString()
        val job =
            scope.launch {
                val result = runCatching { run(block) }
                jobsById.remove(jobId)
                onFinished(jobId, result)
            }
        jobsById[jobId] = job
        return jobId
    }

    /**
     * Cancels one queued or running background job.
     *
     * @param jobId Job identifier returned by [enqueue]
     * @return `true` if the job was found and cancelled, `false` otherwise
     */
    fun cancelJob(jobId: String): Boolean {
        val job = jobsById.remove(jobId) ?: return false
        job.cancel()
        return true
    }

    /**
     * Cancels every queued or running background job.
     *
     * @return Set of cancelled job ids
     */
    fun cancelAllJobs(): Set<String> {
        val snapshot = HashMap(jobsById)
        jobsById.clear()
        snapshot.forEach { (_, job) -> job.cancel() }
        return snapshot.keys
    }

    /**
     * Returns a snapshot of scheduler utilization.
     *
     * @return Active and queued job counts
     */
    fun snapshot(): SubAgentJobQueueSnapshot =
        synchronized(lock) {
            SubAgentJobQueueSnapshot(active = activeJobs, queued = waiting.size)
        }

    private fun dispatchWaitingJobs() {
        val permits = mutableListOf<CompletableDeferred<Unit>>()
        synchronized(lock) {
            val limit = parallelismProvider.get().coerceAtLeast(1)
            while (activeJobs < limit && waiting.isNotEmpty()) {
                activeJobs += 1
                permits += waiting.removeFirst()
            }
        }
        permits.forEach { it.complete(Unit) }
    }

    private companion object {
        const val DISPATCH_INTERVAL_MILLIS = 100L
    }
}

/**
 * Current sub-agent scheduler utilization.
 *
 * @property active Number of jobs currently consuming execution slots
 * @property queued Number of jobs waiting for a slot
 */
data class SubAgentJobQueueSnapshot(
    val active: Int,
    val queued: Int,
)

/**
 * Result produced by a completed sub-agent job.
 *
 * @property agentId Agent that executed the job
 * @property agentName Display name of the executing agent
 * @property content Worker response
 */
data class AgentJobResult(
    val agentId: String,
    val agentName: String,
    val content: String,
)
