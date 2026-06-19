package de.heckenmann.visualagent.knowledge

import java.time.Instant

/**
 * Knowledge entry stored for later retrieval by agents and context tools.
 *
 * Equality is intentionally based on [id] only so embedding byte arrays do not
 * make repository comparisons unstable.
 *
 * @property id Stable storage identifier
 * @property content Human-readable memory content
 * @property tags Search and grouping labels associated with the memory
 * @property createdAt Creation timestamp used for ordering and pruning
 * @property embedding Optional vector representation for semantic search
 */
data class Memory(
    val id: String,
    val content: String,
    val tags: List<String>,
    val createdAt: Instant,
    val embedding: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Memory
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Cached knowledge summary for a workspace or project path.
 *
 * @property id Stable storage identifier
 * @property projectPath Absolute or user-facing path this summary belongs to
 * @property name Optional project display name
 * @property description Optional short project description
 * @property summary Optional accumulated context summary
 * @property lastAccessed Last time the project context was used
 */
data class ProjectKnowledge(
    val id: String,
    val projectPath: String,
    val name: String?,
    val description: String?,
    val summary: String?,
    val lastAccessed: Instant?,
)
