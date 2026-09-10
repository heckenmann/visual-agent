package de.heckenmann.visualagent.knowledge

import java.time.Instant

/** Durable, revisioned reference document owned by the main agent. */
data class MainAgentLongTermMemory(
    val content: String,
    val contentLength: Int,
    val revision: Long,
    val updatedAt: Instant,
)

/** Result of an optimistic main-agent memory replacement. */
sealed interface MainAgentLongTermMemoryEdit {
    /** Successfully persisted replacement. */
    data class Saved(
        val memory: MainAgentLongTermMemory,
    ) : MainAgentLongTermMemoryEdit

    /** The supplied revision is stale. */
    data class Conflict(
        val memory: MainAgentLongTermMemory,
    ) : MainAgentLongTermMemoryEdit
}

/** Stores one bounded, durable document that is injected into main-agent requests. */
interface MainAgentLongTermMemoryStore {
    /** Returns the current durable memory snapshot. */
    fun snapshot(): MainAgentLongTermMemory

    /** Replaces the document only when [expectedRevision] remains current. */
    fun replace(
        content: String,
        expectedRevision: Long,
        maxCodePoints: Int,
    ): MainAgentLongTermMemoryEdit
}

/** In-memory implementation used only by the legacy test-oriented manager constructor. */
internal class InMemoryMainAgentLongTermMemoryStore : MainAgentLongTermMemoryStore {
    private var current = MainAgentLongTermMemory("", 0, 0, Instant.EPOCH)

    @Synchronized
    override fun snapshot(): MainAgentLongTermMemory = current

    @Synchronized
    override fun replace(
        content: String,
        expectedRevision: Long,
        maxCodePoints: Int,
    ): MainAgentLongTermMemoryEdit {
        val contentLength = content.codePointCount(0, content.length)
        require(contentLength <= maxCodePoints) { "Main-agent memory exceeds its configured limit" }
        if (expectedRevision != current.revision) return MainAgentLongTermMemoryEdit.Conflict(current)
        current = MainAgentLongTermMemory(content, contentLength, current.revision + 1, Instant.now())
        return MainAgentLongTermMemoryEdit.Saved(current)
    }
}
