package de.heckenmann.visualagent.agent.tools.api

import kotlinx.serialization.Serializable

/** Metadata returned for one reusable skill. */
@Serializable
data class ToolSkillSummary(
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
    val revision: Long,
    val readCount: Long,
    val lastReadAt: String?,
)

/** Indexed search result returned by the skills tool. */
@Serializable
data class ToolSkillSearchResult(
    val id: String,
    val title: String,
    val snippet: String,
    val updatedAt: String,
    val revision: Long,
    val readCount: Long,
    val lastReadAt: String?,
)

/** Complete skill Markdown returned by a successful model read. */
@Serializable
data class ToolSkillDocument(
    val summary: ToolSkillSummary,
    val content: String,
)

/** Result of a create operation exposed to the model tool. */
sealed interface ToolSkillCreateResult {
    /** A new skill was created. */
    data class Created(
        val skill: ToolSkillSummary,
    ) : ToolSkillCreateResult

    /** An exact duplicate already exists. */
    data class Duplicate(
        val skill: ToolSkillSummary,
    ) : ToolSkillCreateResult
}

/** Result of an update operation exposed to the model tool. */
sealed interface ToolSkillUpdateResult {
    /** The skill was updated. */
    data class Updated(
        val skill: ToolSkillSummary,
    ) : ToolSkillUpdateResult

    /** The expected revision was stale. */
    data class Conflict(
        val skill: ToolSkillSummary,
    ) : ToolSkillUpdateResult

    /** The requested content already exists under another ID. */
    data class Duplicate(
        val skill: ToolSkillSummary,
    ) : ToolSkillUpdateResult

    /** The requested ID does not exist. */
    data object NotFound : ToolSkillUpdateResult
}

/** Result of a delete operation exposed to the model tool. */
sealed interface ToolSkillDeleteResult {
    /** The skill was deleted. */
    data class Deleted(
        val id: String,
        val title: String,
        val revision: Long,
        val deletedAt: String,
    ) : ToolSkillDeleteResult

    /** The expected revision was stale. */
    data class Conflict(
        val skill: ToolSkillSummary,
    ) : ToolSkillDeleteResult

    /** The requested ID does not exist. */
    data object NotFound : ToolSkillDeleteResult
}

/** Server-owned skill capability used by the model-facing skills tool. */
interface SkillToolPort {
    /** Creates a skill or returns metadata for an exact duplicate. */
    fun create(
        title: String,
        content: String,
    ): ToolSkillCreateResult

    /** Searches indexed skill title and Markdown content. */
    fun search(
        query: String,
        limit: Int,
    ): List<ToolSkillSearchResult>

    /** Reads one skill and records the successful model read. */
    fun read(
        id: String,
        isCancelled: () -> Boolean = { false },
    ): ToolSkillDocument?

    /** Updates a skill with optimistic revision checking. */
    fun update(
        id: String,
        expectedRevision: Long,
        title: String,
        content: String,
    ): ToolSkillUpdateResult

    /** Deletes a skill with optimistic revision checking. */
    fun delete(
        id: String,
        expectedRevision: Long,
    ): ToolSkillDeleteResult
}
