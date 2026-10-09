package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.PreferenceStore
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers

/** Execution state of the autonomous sub-agent workers. */
enum class SubAgentExecutionState {
    RUNNING,
    PAUSED,
}

/** Reason why a sub-agent is currently prevented from starting more work. */
enum class SubAgentPauseReason {
    NONE,
    GLOBAL,
    INDIVIDUAL,
    GLOBAL_AND_INDIVIDUAL,
}

/** Immutable view of the global and per-agent execution gates. */
data class SubAgentExecutionSnapshot(
    val globalState: SubAgentExecutionState,
    val pausedAgentIds: Set<String>,
)

/**
 * Combined status for one agent or for the global worker pool.
 *
 * The main agent is intentionally not represented by this state: only sub-agent
 * execution is gated.
 */
data class SubAgentExecutionStatus(
    val agentId: String?,
    val globalState: SubAgentExecutionState,
    val agentState: SubAgentExecutionState?,
    val effectiveState: SubAgentExecutionState,
    val pauseReason: SubAgentPauseReason,
    val pausedAgentIds: Set<String>,
)

/**
 * Authoritative, persistent pause/resume gate shared by the UI, tools, scheduler,
 * and autonomous coordinator.
 *
 * Pausing is cooperative. An operation already inside an LLM request or tool call
 * is allowed to finish; the next safe boundary waits for both gates to be running.
 */
@Service
class SubAgentExecutionControl(
    private val preferenceStore: PreferenceStore,
) {
    private val logger = KotlinLogging.logger {}
    private val lock = Any()
    private val emissionLock = Any()
    private val stateSink = Sinks.many().multicast().directBestEffort<SubAgentExecutionSnapshot>()
    private var stateChanged = Sinks.one<Unit>()
    private var globalPaused: Boolean = loadGlobalPaused()
    private val pausedAgentIds: MutableSet<String> = loadPausedAgentIds().toMutableSet()

    /**
     * Hot stream of execution-gate changes.
     *
     * Changes are not replayed and may be dropped for slow consumers because [snapshot] is the
     * authoritative state query used to recover from a missed refresh signal.
     */
    val stateChanges: Flux<SubAgentExecutionSnapshot> = stateSink.asFlux()

    /** Returns the current global and per-agent execution state. */
    fun snapshot(): SubAgentExecutionSnapshot =
        synchronized(lock) {
            currentSnapshot()
        }

    /** Returns whether the global gate is paused. */
    fun isGloballyPaused(): Boolean = synchronized(lock) { globalPaused }

    /** Returns whether the individual gate for [agentId] is paused. */
    fun isAgentPaused(agentId: String): Boolean = synchronized(lock) { agentId in pausedAgentIds }

    /** Returns whether a worker may cross its next execution boundary. */
    fun isExecutionAllowed(agentId: String? = null): Boolean =
        synchronized(lock) {
            !globalPaused && (agentId == null || agentId !in pausedAgentIds)
        }

    /** Completes at the next boundary where both execution gates allow the worker. */
    fun executionAllowed(agentId: String? = null): Mono<Void> =
        Mono.defer {
            val signal =
                synchronized(lock) {
                    if (!globalPaused && (agentId == null || agentId !in pausedAgentIds)) null else stateChanged
                }
            signal?.asMono()?.then(Mono.defer { executionAllowed(agentId) }) ?: Mono.empty()
        }

    /** Pauses all sub-agent execution without changing individual pause flags. */
    fun pauseAll(): SubAgentExecutionSnapshot = mutate { globalPaused = true }

    /** Persists a global pause through the explicit blocking preference adapter. */
    fun pauseAllReactive(): Mono<SubAgentExecutionSnapshot> = persistReactive { pauseAll() }

    /** Resumes the global gate while preserving individual pause flags. */
    fun resumeAll(): SubAgentExecutionSnapshot = mutate { globalPaused = false }

    /** Resumes global sub-agent execution on the I/O dispatcher. */
    fun resumeAllReactive(): Mono<SubAgentExecutionSnapshot> = persistReactive { resumeAll() }

    /** Pauses one sub-agent execution gate. */
    fun pauseAgent(agentId: String): SubAgentExecutionSnapshot =
        mutate {
            require(agentId.isNotBlank()) { "Agent ID must not be blank" }
            pausedAgentIds += agentId
        }

    /** Pauses one sub-agent execution gate on the I/O dispatcher. */
    fun pauseAgentReactive(agentId: String): Mono<SubAgentExecutionSnapshot> = persistReactive { pauseAgent(agentId) }

    /** Resumes one sub-agent execution gate. */
    fun resumeAgent(agentId: String): SubAgentExecutionSnapshot =
        mutate {
            require(agentId.isNotBlank()) { "Agent ID must not be blank" }
            pausedAgentIds -= agentId
        }

    /** Resumes one sub-agent execution gate on the I/O dispatcher. */
    fun resumeAgentReactive(agentId: String): Mono<SubAgentExecutionSnapshot> = persistReactive { resumeAgent(agentId) }

    private fun persistReactive(change: () -> SubAgentExecutionSnapshot): Mono<SubAgentExecutionSnapshot> =
        Mono.fromCallable(change).subscribeOn(Schedulers.boundedElastic())

    /** Removes an agent's persisted pause state after the agent is deleted. */
    fun removeAgent(agentId: String): SubAgentExecutionSnapshot =
        mutate {
            pausedAgentIds -= agentId
        }

    /** Returns the effective state and pause reason for an optional agent. */
    fun status(agentId: String? = null): SubAgentExecutionStatus =
        synchronized(lock) {
            val individualPaused = agentId != null && agentId in pausedAgentIds
            val effectivePaused = globalPaused || individualPaused
            SubAgentExecutionStatus(
                agentId = agentId,
                globalState = if (globalPaused) SubAgentExecutionState.PAUSED else SubAgentExecutionState.RUNNING,
                agentState = agentId?.let { if (individualPaused) SubAgentExecutionState.PAUSED else SubAgentExecutionState.RUNNING },
                effectiveState = if (effectivePaused) SubAgentExecutionState.PAUSED else SubAgentExecutionState.RUNNING,
                pauseReason =
                    when {
                        globalPaused && individualPaused -> SubAgentPauseReason.GLOBAL_AND_INDIVIDUAL
                        globalPaused -> SubAgentPauseReason.GLOBAL
                        individualPaused -> SubAgentPauseReason.INDIVIDUAL
                        else -> SubAgentPauseReason.NONE
                    },
                pausedAgentIds = pausedAgentIds.toSet(),
            )
        }

    /** Registers a listener for immediate UI/tool state refreshes. */
    fun addListener(listener: (SubAgentExecutionSnapshot) -> Unit): AutoCloseable {
        val subscription =
            stateChanges.subscribe(
                { snapshot ->
                    runCatching { listener(snapshot) }
                        .onFailure { error -> logger.warn(error) { "Sub-agent execution listener failed." } }
                },
                { error -> logger.warn(error) { "Sub-agent execution stream terminated." } },
            )
        return AutoCloseable(subscription::dispose)
    }

    private fun mutate(change: () -> Unit): SubAgentExecutionSnapshot {
        val (next, previousSignal) =
            synchronized(lock) {
                val previousGlobalPaused = globalPaused
                val previousPausedAgentIds = pausedAgentIds.toSet()
                try {
                    change()
                    persistState()
                } catch (error: Throwable) {
                    globalPaused = previousGlobalPaused
                    pausedAgentIds.clear()
                    pausedAgentIds += previousPausedAgentIds
                    runCatching { persistState(previousGlobalPaused, previousPausedAgentIds) }
                    throw error
                }
                val previousSignal = stateChanged
                stateChanged = Sinks.one()
                currentSnapshot() to previousSignal
            }
        previousSignal.tryEmitValue(Unit)
        synchronized(emissionLock) {
            val result = stateSink.tryEmitNext(next)
            if (result != Sinks.EmitResult.OK) logger.warn { "Unable to emit sub-agent execution state: $result" }
        }
        return next
    }

    private fun currentSnapshot() =
        SubAgentExecutionSnapshot(
            globalState = if (globalPaused) SubAgentExecutionState.PAUSED else SubAgentExecutionState.RUNNING,
            pausedAgentIds = pausedAgentIds.toSet(),
        )

    private fun persistState(
        globalPaused: Boolean = this.globalPaused,
        pausedAgentIds: Set<String> = this.pausedAgentIds,
    ) {
        preferenceStore.setPreference(GLOBAL_PAUSED_KEY, globalPaused.toString())
        preferenceStore.setPreference(PAUSED_AGENTS_KEY, pausedAgentIds.sorted().joinToString("\n"))
    }

    private fun loadGlobalPaused(): Boolean =
        preferenceStore
            .getPreference(GLOBAL_PAUSED_KEY)
            ?.trim()
            ?.equals("true", ignoreCase = true)
            ?: false

    private fun loadPausedAgentIds(): Set<String> =
        preferenceStore
            .getPreference(PAUSED_AGENTS_KEY)
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

    private companion object {
        const val GLOBAL_PAUSED_KEY = "agent.execution.pause.global.v1"
        const val PAUSED_AGENTS_KEY = "agent.execution.pause.agents.v1"
    }
}
