package de.heckenmann.visualagent.knowledge

import java.time.Instant

/**
 * Represents Memory.
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
 * Represents ProjectKnowledge.
 */
data class ProjectKnowledge(
    val id: String,
    val projectPath: String,
    val name: String?,
    val description: String?,
    val summary: String?,
    val lastAccessed: Instant?,
)
