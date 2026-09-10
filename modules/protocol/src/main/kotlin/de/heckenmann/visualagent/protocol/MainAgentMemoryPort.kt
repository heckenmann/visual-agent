package de.heckenmann.visualagent.protocol

/** Server-owned editable durable reference document for the main agent. */
interface MainAgentMemoryPort {
    /** Returns the latest memory document and its optimistic revision. */
    fun snapshot(): MainAgentMemorySnapshot

    /** Replaces the document only when [expectedRevision] is current. */
    fun replace(
        content: String,
        expectedRevision: Long,
    ): MainAgentMemoryUpdate
}

/** Durable main-agent memory visible to the presentation and model layers. */
data class MainAgentMemorySnapshot(
    val content: String,
    val contentLength: Int,
    val limit: Int,
    val revision: Long,
)

/** Result of an optimistic durable-memory update. */
sealed interface MainAgentMemoryUpdate {
    /** A replacement committed successfully. */
    data class Saved(
        val snapshot: MainAgentMemorySnapshot,
    ) : MainAgentMemoryUpdate

    /** A replacement was rejected because the document changed concurrently. */
    data class Conflict(
        val snapshot: MainAgentMemorySnapshot,
    ) : MainAgentMemoryUpdate
}
