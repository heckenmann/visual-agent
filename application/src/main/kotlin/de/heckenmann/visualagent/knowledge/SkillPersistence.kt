package de.heckenmann.visualagent.knowledge

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/** Persisted reusable Markdown knowledge authored by a model or user. */
data class SkillRecord(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Long,
    val readCount: Long,
    val lastReadAt: Instant?,
)

/** Bounded indexed result for a skill search. */
data class SkillSearchRecord(
    val id: String,
    val title: String,
    val snippet: String,
    val updatedAt: Instant,
    val revision: Long,
    val readCount: Long,
    val lastReadAt: Instant?,
)

/** Outcome of creating a skill. */
sealed interface SkillCreateResult {
    /** A new skill was stored. */
    data class Created(
        val skill: SkillRecord,
    ) : SkillCreateResult

    /** An exact duplicate already exists. */
    data class Duplicate(
        val skill: SkillRecord,
    ) : SkillCreateResult
}

/** Outcome of updating a skill. */
sealed interface SkillUpdateResult {
    /** The skill was updated. */
    data class Updated(
        val skill: SkillRecord,
    ) : SkillUpdateResult

    /** The supplied revision did not match the stored revision. */
    data class Conflict(
        val skill: SkillRecord,
    ) : SkillUpdateResult

    /** An exact duplicate already exists under another ID. */
    data class Duplicate(
        val skill: SkillRecord,
    ) : SkillUpdateResult

    /** No skill exists for the requested ID. */
    data object NotFound : SkillUpdateResult
}

/** Outcome of deleting a skill. */
sealed interface SkillDeleteResult {
    /** The skill was deleted and its minimal audit record was written. */
    data class Deleted(
        val id: String,
        val title: String,
        val revision: Long,
        val deletedAt: Instant,
    ) : SkillDeleteResult

    /** The supplied revision did not match the stored revision. */
    data class Conflict(
        val skill: SkillRecord,
    ) : SkillDeleteResult

    /** No skill exists for the requested ID. */
    data object NotFound : SkillDeleteResult
}

/** Stores, searches, reads, updates, and deletes reusable Markdown skills. */
interface SkillStore {
    /** Creates a skill or returns the existing exact duplicate. */
    fun createSkill(
        title: String,
        content: String,
    ): SkillCreateResult

    /** Searches title and Markdown content using the database FTS index. */
    fun searchSkills(
        query: String,
        limit: Int = 5,
    ): List<SkillSearchRecord>

    /** Returns a skill without recording model-read telemetry. */
    fun getSkill(id: String): SkillRecord?

    /** Returns a skill and atomically records one successful model read. */
    fun readSkill(
        id: String,
        isCancelled: () -> Boolean = { false },
    ): SkillRecord?

    /** Updates a skill only when the expected revision is current. */
    fun updateSkill(
        id: String,
        expectedRevision: Long,
        title: String,
        content: String,
    ): SkillUpdateResult

    /** Deletes a skill only when the expected revision is current. */
    fun deleteSkill(
        id: String,
        expectedRevision: Long,
    ): SkillDeleteResult

    /** Reactive counterpart of [createSkill]. */
    fun createSkillReactive(
        title: String,
        content: String,
    ): Mono<SkillCreateResult> = Mono.fromCallable { createSkill(title, content) }

    /** Reactive counterpart of [searchSkills]. */
    fun searchSkillsReactive(
        query: String,
        limit: Int = 5,
    ): Flux<SkillSearchRecord> = Flux.defer { Flux.fromIterable(searchSkills(query, limit)) }

    /** Reactive counterpart of [getSkill]. */
    fun getSkillReactive(id: String): Mono<SkillRecord> = Mono.fromCallable { getSkill(id) }

    /** Reactive counterpart of [readSkill]. */
    fun readSkillReactive(
        id: String,
        isCancelled: () -> Boolean = { false },
    ): Mono<SkillRecord> = Mono.fromCallable { readSkill(id, isCancelled) }

    /** Reactive counterpart of [updateSkill]. */
    fun updateSkillReactive(
        id: String,
        expectedRevision: Long,
        title: String,
        content: String,
    ): Mono<SkillUpdateResult> = Mono.fromCallable { updateSkill(id, expectedRevision, title, content) }

    /** Reactive counterpart of [deleteSkill]. */
    fun deleteSkillReactive(
        id: String,
        expectedRevision: Long,
    ): Mono<SkillDeleteResult> = Mono.fromCallable { deleteSkill(id, expectedRevision) }
}
