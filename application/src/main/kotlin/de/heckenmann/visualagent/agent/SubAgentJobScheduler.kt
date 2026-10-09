package de.heckenmann.visualagent.agent

import reactor.core.Disposable
import reactor.core.Disposables
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Schedules sub-agent jobs against the user-configured parallelism limit.
 *
 * Waiting jobs are admitted in FIFO order and dispatched when work, capacity, or execution
 * gates change.
 *
 * @property parallelismProvider Current maximum number of concurrently running sub-agent jobs
 */
class SubAgentJobScheduler(
    private val parallelismProvider: ParallelismProvider,
    private val executionControl: SubAgentExecutionControl? = null,
) : AutoCloseable {
    private val closed =
        java.util.concurrent.atomic
            .AtomicBoolean(false)
    private val shutdown = Sinks.one<Void>()
    private val lock = Any()
    private val waiting = ArrayDeque<WaitingJob>()
    private var activeJobs = 0
    private val jobsById = ConcurrentHashMap<String, Disposable>()
    private val subscriptions = mutableListOf<AutoCloseable>()
    private val queueListeners = CopyOnWriteArrayList<(SubAgentJobQueueSnapshot) -> Unit>()

    init {
        executionControl?.let { control ->
            subscriptions += control.addListener { dispatchWaitingJobs() }
        }
        subscriptions += parallelismProvider.addChangeListener { dispatchWaitingJobs() }
    }

    /** Releases scheduler subscriptions and cancels queued or running jobs. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        shutdown.tryEmitError(CancellationException("Scheduler closed"))
        synchronized(lock) {
            subscriptions.forEach(AutoCloseable::close)
            subscriptions.clear()
        }
        cancelAllJobs()
    }

    /** Runs a Reactor-native operation through the shared capacity and pause gates. */
    fun <T : Any> runReactive(
        agentId: String?,
        requestId: String?,
        block: () -> Mono<T>,
    ): Mono<T> =
        Mono.defer {
            if (closed.get()) return@defer Mono.error(CancellationException("Scheduler closed"))
            Mono.firstWithSignal(
                Mono.using(
                    { WaitingJob(agentId, requestId, Sinks.one()) },
                    { job ->
                        val accepted =
                            synchronized(lock) {
                                if (closed.get()) {
                                    false
                                } else {
                                    waiting.addLast(job)
                                    true
                                }
                            }
                        if (!accepted) return@using Mono.error<T>(CancellationException("Scheduler closed"))
                        publishSnapshot()
                        dispatchWaitingJobs()
                        job.permit
                            .asMono()
                            .then(executionControl?.executionAllowed(agentId) ?: Mono.empty())
                            .then(Mono.defer(block))
                    },
                    { job ->
                        synchronized(lock) {
                            waiting.remove(job)
                            if (job.dispatched) activeJobs = (activeJobs - 1).coerceAtLeast(0)
                        }
                        publishSnapshot()
                        dispatchWaitingJobs()
                    },
                ),
                shutdown.asMono().then(Mono.error<T>(CancellationException("Scheduler closed"))),
            )
        }

    /** Registers and subscribes one background Reactor operation, reporting cancellation once. */
    fun <T : Any> enqueueReactive(
        agentId: String? = null,
        block: () -> Mono<T>,
        onFinished: (jobId: String, result: Result<T>) -> Unit,
    ): String {
        val jobId = UUID.randomUUID().toString()
        val job = Disposables.swap()
        val finished = AtomicBoolean(false)
        jobsById[jobId] = job

        /** Removes registration before invoking the terminal callback exactly once. */
        fun report(result: Result<T>) {
            if (finished.compareAndSet(false, true)) {
                jobsById.remove(jobId, job)
                onFinished(jobId, result)
            }
        }
        val outcome =
            AtomicReference<Result<T>>(
                Result.failure(IllegalStateException("Background operation returned no result")),
            )

        /** Isolates synchronous completion persistence from provider and cancellation threads. */
        fun finish(result: Result<T>): Mono<Void> =
            Mono
                .fromRunnable<Void> { report(result) }
                .subscribeOn(Schedulers.boundedElastic())
        val pipeline =
            Mono.usingWhen(
                Mono.just(jobId),
                {
                    runReactive(agentId, jobId) { Mono.defer(block).subscribeOn(Schedulers.boundedElastic()) }
                        .doOnNext { outcome.set(Result.success(it)) }
                },
                { finish(outcome.get()) },
                { _, error -> finish(Result.failure(error)) },
                { finish(Result.failure(CancellationException("Queued operation was cancelled."))) },
            )
        job.update(
            pipeline.subscribe({}, { error ->
                mu.KotlinLogging.logger {}.warn(error) { "Background job $jobId terminated" }
            }),
        )
        return jobId
    }

    /**
     * Cancels one queued or running background job.
     *
     * @param jobId Job identifier returned by [enqueueReactive]
     * @return `true` if the job was found and cancelled, `false` otherwise
     */
    fun cancelJob(jobId: String): Boolean {
        val job = jobsById.remove(jobId) ?: return false
        job.dispose()
        return true
    }

    /** Removes and cancels queued work belonging to one request without affecting other jobs. */
    fun cancelQueuedRequest(requestId: String): Int {
        val cancelled =
            synchronized(lock) {
                waiting.filter { it.requestId == requestId }.also { jobs -> jobs.forEach(waiting::remove) }
            }
        cancelled.forEach { it.permit.tryEmitError(CancellationException("Queued operation was cancelled.")) }
        if (cancelled.isNotEmpty()) publishSnapshot()
        return cancelled.size
    }

    /** Observes queue snapshots after a queued job is added, removed, or dispatched. */
    internal fun addQueueListener(listener: (SubAgentJobQueueSnapshot) -> Unit): AutoCloseable {
        queueListeners += listener
        listener(snapshot())
        return AutoCloseable { queueListeners -= listener }
    }

    /**
     * Cancels every queued or running background job.
     *
     * @return Set of cancelled job ids
     */
    fun cancelAllJobs(): Set<String> {
        val snapshot = HashMap(jobsById)
        jobsById.clear()
        snapshot.forEach { (_, job) -> job.dispose() }
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
        if (closed.get()) return
        val permits = mutableListOf<Sinks.One<Unit>>()
        synchronized(lock) {
            val limit = parallelismProvider.get().coerceAtLeast(1)
            while (activeJobs < limit && waiting.isNotEmpty()) {
                val next = waiting.firstOrNull { isExecutionAllowed(it.agentId) } ?: break
                waiting.remove(next)
                next.dispatched = true
                activeJobs += 1
                permits += next.permit
            }
        }
        if (permits.isNotEmpty()) {
            publishSnapshot()
            permits.forEach { it.tryEmitValue(Unit) }
        }
    }

    private fun publishSnapshot() {
        val current = snapshot()
        queueListeners.forEach { listener -> runCatching { listener(current) } }
    }

    private fun isExecutionAllowed(agentId: String?): Boolean = executionControl?.isExecutionAllowed(agentId) != false

    private data class WaitingJob(
        val agentId: String?,
        val requestId: String?,
        val permit: Sinks.One<Unit>,
        var dispatched: Boolean = false,
    )
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
