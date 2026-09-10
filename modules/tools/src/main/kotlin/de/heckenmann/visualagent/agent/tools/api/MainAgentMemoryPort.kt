package de.heckenmann.visualagent.agent.tools.api

import kotlinx.serialization.Serializable

/** Snapshot of the durable document owned by the main agent. */
@Serializable
data class MainAgentMemorySnapshot(
    val content: String,
    val revision: Long,
    val size: Int,
    val limit: Int,
)

/** Result of a revision-safe main-agent memory edit. */
sealed interface MainAgentMemoryEditResult {
    /** Successfully saved snapshot. */
    data class Saved(
        val snapshot: MainAgentMemorySnapshot,
    ) : MainAgentMemoryEditResult

    /** Current snapshot returned when the expected revision is stale. */
    data class Conflict(
        val snapshot: MainAgentMemorySnapshot,
    ) : MainAgentMemoryEditResult
}

/** Server-owned capability for the main model's durable memory document. */
interface MainAgentMemoryPort {
    /** Returns the current bounded snapshot. */
    fun show(): MainAgentMemorySnapshot

    /** Replaces the document atomically when [expectedRevision] matches. */
    fun edit(
        content: String,
        expectedRevision: Long,
    ): MainAgentMemoryEditResult
}
