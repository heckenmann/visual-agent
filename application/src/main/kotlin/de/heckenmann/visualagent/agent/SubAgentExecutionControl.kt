package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.PreferenceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArrayList

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
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(SubAgentExecutionSnapshot) -> Unit>()
    private var stateChanged = CompletableDeferred<Unit>()
    private var globalPaused: Boolean = loadGlobalPaused()
    private val pausedAgentIds: MutableSet<String> = loadPausedAgentIds().toMutableSet()

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

    /**
     * Suspends until both the global and optional individual gate allow execution.
     *
     * @param agentId Worker identity, or null for a temporary/global-only job
     */
    suspend fun awaitExecutionAllowed(agentId: String? = null) {
        while (true) {
            val signal =
                synchronized(lock) {
                    if (!globalPaused && (agentId == null || agentId !in pausedAgentIds)) return
                    stateChanged
                }
            signal.await()
        }
    }

    /** Pauses all sub-agent execution without changing individual pause flags. */
    fun pauseAll(): SubAgentExecutionSnapshot = mutate { globalPaused = true }

    /**
     * Pauses all sub-agent execution on the I/O dispatcher.
     *
     * This variant is intended for UI callers because the mutation persists two
     * preferences and notifies listeners synchronously.
     */
    suspend fun pauseAllAsync(): SubAgentExecutionSnapshot = withContext(Dispatchers.IO) { pauseAll() }

    /** Resumes the global gate while preserving individual pause flags. */
    fun resumeAll(): SubAgentExecutionSnapshot = mutate { globalPaused = false }

    /** Resumes global sub-agent execution on the I/O dispatcher. */
    suspend fun resumeAllAsync(): SubAgentExecutionSnapshot = withContext(Dispatchers.IO) { resumeAll() }

    /** Pauses one sub-agent execution gate. */
    fun pauseAgent(agentId: String): SubAgentExecutionSnapshot =
        mutate {
            require(agentId.isNotBlank()) { "Agent ID must not be blank" }
            pausedAgentIds += agentId
        }

    /** Pauses one sub-agent execution gate on the I/O dispatcher. */
    suspend fun pauseAgentAsync(agentId: String): SubAgentExecutionSnapshot =
        withContext(Dispatchers.IO) {
            pauseAgent(agentId)
        }

    /** Resumes one sub-agent execution gate. */
    fun resumeAgent(agentId: String): SubAgentExecutionSnapshot =
        mutate {
            require(agentId.isNotBlank()) { "Agent ID must not be blank" }
            pausedAgentIds -= agentId
        }

    /** Resumes one sub-agent execution gate on the I/O dispatcher. */
    suspend fun resumeAgentAsync(agentId: String): SubAgentExecutionSnapshot =
        withContext(Dispatchers.IO) {
            resumeAgent(agentId)
        }

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
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    private fun mutate(change: () -> Unit): SubAgentExecutionSnapshot {
        val next =
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
                stateChanged = CompletableDeferred()
                previousSignal.complete(Unit)
                currentSnapshot()
            }
        listeners.forEach { listener -> runCatching { listener(next) } }
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
