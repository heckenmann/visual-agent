package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockList
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockNullable
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockRequired
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** R2DBC implementation for the durable searchable skill catalog. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcSkillStore(
    private val databaseClient: DatabaseClient,
    private val transactionOperator: TransactionalOperator,
) : SkillStore {
    override fun createSkill(
        title: String,
        content: String,
    ): SkillCreateResult = createSkillReactive(title, content).blockRequired()

    override fun searchSkills(
        query: String,
        limit: Int,
    ): List<SkillSearchRecord> = searchSkillsReactive(query, limit).blockList()

    override fun getSkill(id: String): SkillRecord? = getSkillReactive(id).blockNullable()

    override fun readSkill(
        id: String,
        isCancelled: () -> Boolean,
    ): SkillRecord? = readSkillReactive(id, isCancelled).blockNullable()

    override fun updateSkill(
        id: String,
        expectedRevision: Long,
        title: String,
        content: String,
    ): SkillUpdateResult = updateSkillReactive(id, expectedRevision, title, content).blockRequired()

    override fun deleteSkill(
        id: String,
        expectedRevision: Long,
    ): SkillDeleteResult = deleteSkillReactive(id, expectedRevision).blockRequired()

    override fun createSkillReactive(
        title: String,
        content: String,
    ): Mono<SkillCreateResult> {
        val normalizedTitle = validateTitle(title)
        validateContent(content)
        val fingerprint = fingerprint(normalizedTitle, content)
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        return transactionOperator.transactional(
            databaseClient
                .sql(
                    """
                    INSERT INTO skills (id, title, content, content_fingerprint, created_at, updated_at, revision, read_count, last_read_at)
                    SELECT :id, :title, :content, :fingerprint, :createdAt, :updatedAt, 1, 0, NULL
                    WHERE NOT EXISTS (SELECT 1 FROM skills WHERE content_fingerprint = :fingerprint)
                    """.trimIndent(),
                ).bind("id", id)
                .bind("title", normalizedTitle)
                .bind("content", content)
                .bind("fingerprint", fingerprint)
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .fetch()
                .rowsUpdated()
                .then(findByFingerprint(fingerprint))
                .map { skill -> if (skill.id == id) SkillCreateResult.Created(skill) else SkillCreateResult.Duplicate(skill) },
        )
    }

    override fun searchSkillsReactive(
        query: String,
        limit: Int,
    ): Flux<SkillSearchRecord> {
        validateQuery(query)
        val boundedLimit = limit.coerceIn(1, MAX_SEARCH_RESULTS)
        val statement =
            if (query.isBlank()) {
                databaseClient
                    .sql(
                        "SELECT id, title, content, created_at, updated_at, revision, read_count, last_read_at FROM skills ORDER BY updated_at DESC, id ASC LIMIT :limit",
                    ).bind("limit", boundedLimit)
            } else {
                databaseClient
                    .sql(
                        "SELECT id, title, content, created_at, updated_at, revision, read_count, last_read_at FROM skills WHERE lower(title) LIKE :query ESCAPE '\\' OR lower(content) LIKE :query ESCAPE '\\' ORDER BY updated_at DESC, id ASC LIMIT :limit",
                    ).bind("query", likePattern(query.trim()))
                    .bind("limit", boundedLimit)
            }
        return statement
            .map { row, _ -> row.toSkillRecord() }
            .all()
            .map { skill -> skill.toSearchRecord(skill.content.takeCodePoints(MAX_SNIPPET_CODE_POINTS)) }
    }

    override fun getSkillReactive(id: String): Mono<SkillRecord> = selectById(requireUuid(id))

    override fun readSkillReactive(
        id: String,
        isCancelled: () -> Boolean,
    ): Mono<SkillRecord> {
        val validId = requireUuid(id)
        return Mono.defer {
            check(!isCancelled()) { "Skill read was cancelled" }
            databaseClient
                .sql("UPDATE skills SET read_count = read_count + 1, last_read_at = :readAt WHERE id = :id")
                .bind("readAt", Instant.now().toString())
                .bind("id", validId)
                .fetch()
                .rowsUpdated()
                .flatMap { updated ->
                    check(!isCancelled()) { "Skill read was cancelled" }
                    if (updated == 0L) Mono.empty() else selectById(validId)
                }
        }
    }

    override fun updateSkillReactive(
        id: String,
        expectedRevision: Long,
        title: String,
        content: String,
    ): Mono<SkillUpdateResult> {
        val validId = requireUuid(id)
        val normalizedTitle = validateTitle(title)
        validateContent(content)
        val fingerprint = fingerprint(normalizedTitle, content)
        return transactionOperator.transactional(
            databaseClient
                .sql(
                    """
                    UPDATE skills
                    SET title = :title, content = :content, content_fingerprint = :fingerprint,
                        updated_at = :updatedAt, revision = revision + 1
                    WHERE id = :id AND revision = :expectedRevision
                      AND NOT EXISTS (
                          SELECT 1 FROM skills duplicate
                          WHERE duplicate.content_fingerprint = :fingerprint AND duplicate.id <> :id
                      )
                    """.trimIndent(),
                ).bind("title", normalizedTitle)
                .bind("content", content)
                .bind("fingerprint", fingerprint)
                .bind("updatedAt", Instant.now().toString())
                .bind("id", validId)
                .bind("expectedRevision", expectedRevision)
                .fetch()
                .rowsUpdated()
                .flatMap { updated ->
                    if (updated == 1L) {
                        selectById(validId).map { SkillUpdateResult.Updated(it) }
                    } else {
                        findByFingerprint(fingerprint)
                            .filter { it.id != validId }
                            .map<SkillUpdateResult> { SkillUpdateResult.Duplicate(it) }
                            .switchIfEmpty(
                                selectById(validId)
                                    .map<SkillUpdateResult> { SkillUpdateResult.Conflict(it) }
                                    .switchIfEmpty(Mono.just(SkillUpdateResult.NotFound)),
                            )
                    }
                },
        )
    }

    override fun deleteSkillReactive(
        id: String,
        expectedRevision: Long,
    ): Mono<SkillDeleteResult> {
        val validId = requireUuid(id)
        return transactionOperator.transactional(
            selectById(validId)
                .map { it }
                .flatMap { current ->
                    if (current.revision != expectedRevision) {
                        Mono.just(SkillDeleteResult.Conflict(current))
                    } else {
                        val deletedAt = Instant.now()
                        databaseClient
                            .sql("DELETE FROM skills WHERE id = :id AND revision = :expectedRevision")
                            .bind("id", validId)
                            .bind("expectedRevision", expectedRevision)
                            .fetch()
                            .rowsUpdated()
                            .flatMap { deleted ->
                                if (deleted != 1L) {
                                    selectById(validId)
                                        .map<SkillDeleteResult> { SkillDeleteResult.Conflict(it) }
                                        .switchIfEmpty(Mono.just(SkillDeleteResult.NotFound))
                                } else {
                                    databaseClient
                                        .sql(
                                            "INSERT INTO deleted_skill_audit (skill_id, title, revision, deleted_at) VALUES (:id, :title, :revision, :deletedAt)",
                                        ).bind("id", validId)
                                        .bind("title", current.title)
                                        .bind("revision", current.revision)
                                        .bind("deletedAt", deletedAt.toString())
                                        .fetch()
                                        .rowsUpdated()
                                        .thenReturn<SkillDeleteResult>(
                                            SkillDeleteResult.Deleted(validId, current.title, current.revision, deletedAt),
                                        )
                                }
                            }
                    }
                }.switchIfEmpty(Mono.just(SkillDeleteResult.NotFound)),
        )
    }

    private fun selectById(id: String): Mono<SkillRecord> =
        databaseClient
            .sql("SELECT id, title, content, created_at, updated_at, revision, read_count, last_read_at FROM skills WHERE id = :id")
            .bind("id", id)
            .map { row, _ -> row.toSkillRecord() }
            .one()

    private fun findByFingerprint(fingerprint: String): Mono<SkillRecord> =
        databaseClient
            .sql(
                "SELECT id, title, content, created_at, updated_at, revision, read_count, last_read_at FROM skills WHERE content_fingerprint = :fingerprint",
            ).bind("fingerprint", fingerprint)
            .map { row, _ -> row.toSkillRecord() }
            .one()

    private fun validateTitle(title: String): String {
        val normalized = title.trim()
        require(normalized.isNotBlank() && normalized.codePointCount(0, normalized.length) <= MAX_TITLE_CODE_POINTS) {
            "Skill title must contain 1-$MAX_TITLE_CODE_POINTS Unicode code points"
        }
        return normalized
    }

    private fun validateContent(content: String) {
        require(content.isNotBlank() && content.codePointCount(0, content.length) <= MAX_CONTENT_CODE_POINTS) {
            "Skill Markdown must contain 1-$MAX_CONTENT_CODE_POINTS Unicode code points"
        }
    }

    private fun validateQuery(query: String) {
        require(query.codePointCount(0, query.length) <= MAX_QUERY_CODE_POINTS) {
            "Skill search query exceeds $MAX_QUERY_CODE_POINTS Unicode code points"
        }
    }

    private fun requireUuid(id: String): String =
        runCatching { UUID.fromString(id).toString() }.getOrElse { throw IllegalArgumentException("Skill id must be a canonical UUID") }

    private fun fingerprint(
        title: String,
        content: String,
    ): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest((title + "\u0000" + content).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun likePattern(query: String): String =
        "%" +
            query
                .lowercase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%"

    private companion object {
        const val MAX_TITLE_CODE_POINTS = 200
        const val MAX_CONTENT_CODE_POINTS = 120_000
        const val MAX_QUERY_CODE_POINTS = 500
        const val MAX_SEARCH_RESULTS = 25
        const val MAX_SNIPPET_CODE_POINTS = 320
    }
}
