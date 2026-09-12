package de.heckenmann.visualagent.protocol

/** Server-owned reusable Markdown skill catalog used by the skills panel. */
interface SkillPort {
    /** Lists skills, optionally matching the title and indexed Markdown content. */
    fun search(
        query: String = "",
        limit: Int = 25,
    ): List<SkillSearchResult>

    /** Loads a complete skill without changing its model-read statistics. */
    fun get(id: String): SkillDocument?

    /** Creates a skill or returns the exact duplicate already stored. */
    fun create(
        title: String,
        content: String,
    ): SkillCreateResult

    /** Updates a skill only when its revision is still current. */
    fun update(
        id: String,
        expectedRevision: Long,
        title: String,
        content: String,
    ): SkillUpdateResult

    /** Deletes a skill only when its revision is still current. */
    fun delete(
        id: String,
        expectedRevision: Long,
    ): SkillDeleteResult
}

/** Bounded metadata returned by skill search and catalog listings. */
data class SkillSearchResult(
    val id: String,
    val title: String,
    val snippet: String,
    val updatedAt: String,
    val revision: Long,
    val readCount: Long,
    val lastReadAt: String?,
)

/** Complete Markdown document shown in the skill detail view. */
data class SkillDocument(
    val summary: SkillSearchResult,
    val content: String,
)

/** Result of creating a skill. */
sealed interface SkillCreateResult {
    /** A new skill was persisted. */
    data class Created(
        val skill: SkillSearchResult,
    ) : SkillCreateResult

    /** An exact content duplicate already exists. */
    data class Duplicate(
        val skill: SkillSearchResult,
    ) : SkillCreateResult
}

/** Result of updating a skill. */
sealed interface SkillUpdateResult {
    /** The update was persisted. */
    data class Updated(
        val skill: SkillSearchResult,
    ) : SkillUpdateResult

    /** The editor used a stale revision. */
    data class Conflict(
        val skill: SkillSearchResult,
    ) : SkillUpdateResult

    /** Equivalent content already exists under another skill ID. */
    data class Duplicate(
        val skill: SkillSearchResult,
    ) : SkillUpdateResult

    /** The skill no longer exists. */
    data object NotFound : SkillUpdateResult
}

/** Result of deleting a skill. */
sealed interface SkillDeleteResult {
    /** The skill was deleted. */
    data class Deleted(
        val id: String,
        val title: String,
        val revision: Long,
    ) : SkillDeleteResult

    /** The delete request used a stale revision. */
    data class Conflict(
        val skill: SkillSearchResult,
    ) : SkillDeleteResult

    /** The skill no longer exists. */
    data object NotFound : SkillDeleteResult
}
