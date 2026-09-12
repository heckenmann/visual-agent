package de.heckenmann.visualagent.knowledge

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** SQLite/JPA implementation of the durable searchable skill catalog. */
@Service
internal class JpaSkillStore(
    private val repository: SkillRepository,
    private val deletedAuditRepository: DeletedSkillAuditRepository,
) : SkillStore {
    @Transactional
    override fun createSkill(
        title: String,
        content: String,
    ): SkillCreateResult {
        val normalizedTitle = validateTitle(title)
        validateContent(content)
        val fingerprint = fingerprint(normalizedTitle, content)
        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        val inserted = repository.insertIfAbsent(id, normalizedTitle, content, fingerprint, now.toString(), now.toString())
        val stored = repository.findByContentFingerprint(fingerprint) ?: error("Skill insert was not visible after commit")
        return if (inserted == 1) SkillCreateResult.Created(stored.toRecord()) else SkillCreateResult.Duplicate(stored.toRecord())
    }

    @Transactional(readOnly = true)
    override fun searchSkills(
        query: String,
        limit: Int,
    ): List<SkillSearchRecord> {
        validateQuery(query)
        val boundedLimit = limit.coerceIn(1, MAX_SEARCH_RESULTS)
        val rows =
            if (query.isBlank()) {
                repository
                    .findAllByOrderByUpdatedAtDescIdAsc(PageRequest.of(0, boundedLimit))
                    .map { SkillSearchRow(it.id, it.content.takeCodePoints(MAX_SNIPPET_CODE_POINTS)) }
            } else {
                runCatching { repository.searchFts(toFtsQuery(query), boundedLimit) }
                    .getOrElse { repository.searchLike(query.trim(), boundedLimit) }
            }
        val entities = repository.findAllById(rows.map(SkillSearchRow::id)).associateBy(SkillEntity::id)
        return rows.mapNotNull { row -> entities[row.id]?.toSearchRecord(row.snippet) }
    }

    @Transactional(readOnly = true)
    override fun getSkill(id: String): SkillRecord? = repository.findById(requireUuid(id)).orElse(null)?.toRecord()

    @Transactional
    override fun readSkill(
        id: String,
        isCancelled: () -> Boolean,
    ): SkillRecord? {
        val validId = requireUuid(id)
        check(!isCancelled()) { "Skill read was cancelled" }
        if (!repository.existsById(validId)) return null
        val now = Instant.now()
        check(!isCancelled()) { "Skill read was cancelled" }
        if (repository.recordRead(validId, now) != 1) return null
        check(!isCancelled()) { "Skill read was cancelled" }
        return repository.findById(validId).orElse(null)?.toRecord()
    }

    @Transactional
    override fun updateSkill(
        id: String,
        expectedRevision: Long,
        title: String,
        content: String,
    ): SkillUpdateResult {
        val validId = requireUuid(id)
        val normalizedTitle = validateTitle(title)
        validateContent(content)
        val fingerprint = fingerprint(normalizedTitle, content)
        repository.findByContentFingerprint(fingerprint)?.let {
            if (it.id != validId) return SkillUpdateResult.Duplicate(it.toRecord())
        }
        val updated =
            repository.updateIfRevisionMatches(
                validId,
                expectedRevision,
                normalizedTitle,
                content,
                fingerprint,
                Instant.now().toString(),
            )
        if (updated == 1) {
            return SkillUpdateResult.Updated(repository.findById(validId).orElseThrow().toRecord())
        }
        repository.findByContentFingerprint(fingerprint)?.let {
            if (it.id != validId) return SkillUpdateResult.Duplicate(it.toRecord())
        }
        val current = repository.findById(validId).orElse(null) ?: return SkillUpdateResult.NotFound
        return SkillUpdateResult.Conflict(current.toRecord())
    }

    @Transactional
    override fun deleteSkill(
        id: String,
        expectedRevision: Long,
    ): SkillDeleteResult {
        val validId = requireUuid(id)
        val current = repository.findById(validId).orElse(null) ?: return SkillDeleteResult.NotFound
        if (current.revision != expectedRevision) return SkillDeleteResult.Conflict(current.toRecord())
        val deletedAt = Instant.now()
        if (repository.deleteIfRevisionMatches(validId, expectedRevision) != 1) {
            val latest = repository.findById(validId).orElse(null) ?: return SkillDeleteResult.NotFound
            return SkillDeleteResult.Conflict(latest.toRecord())
        }
        deletedAuditRepository.save(DeletedSkillAuditEntity(validId, current.title, current.revision, deletedAt))
        return SkillDeleteResult.Deleted(validId, current.title, current.revision, deletedAt)
    }

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

    private fun toFtsQuery(query: String): String =
        query.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(MAX_QUERY_TERMS).joinToString(" AND ") { term ->
            "\"${term.replace("\"", "\"\"")}\""
        }

    private fun fingerprint(
        title: String,
        content: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest((title + "\u0000" + content).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_TITLE_CODE_POINTS = 200
        const val MAX_CONTENT_CODE_POINTS = 120_000
        const val MAX_QUERY_CODE_POINTS = 500
        const val MAX_QUERY_TERMS = 20
        const val MAX_SEARCH_RESULTS = 25
        const val MAX_SNIPPET_CODE_POINTS = 320
    }
}

internal interface DeletedSkillAuditRepository : org.springframework.data.jpa.repository.JpaRepository<DeletedSkillAuditEntity, String>

private fun SkillEntity.toRecord(): SkillRecord = SkillRecord(id, title, content, createdAt, updatedAt, revision, readCount, lastReadAt)

private fun SkillEntity.toSearchRecord(snippet: String): SkillSearchRecord =
    SkillSearchRecord(id, title, snippet.takeCodePoints(320), updatedAt, revision, readCount, lastReadAt)

private fun String.takeCodePoints(maximum: Int): String {
    if (codePointCount(0, length) <= maximum) return this
    return substring(0, offsetByCodePoints(0, maximum))
}
