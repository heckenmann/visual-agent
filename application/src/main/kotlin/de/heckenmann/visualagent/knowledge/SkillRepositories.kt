package de.heckenmann.visualagent.knowledge

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

internal interface SkillRepository :
    JpaRepository<SkillEntity, String>,
    SkillRepositoryCustom {
    fun findByContentFingerprint(contentFingerprint: String): SkillEntity?

    fun findAllByOrderByUpdatedAtDescIdAsc(pageable: Pageable): List<SkillEntity>

    /** Inserts one skill atomically, returning zero when the fingerprint already exists. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value =
            """
            INSERT INTO skills (id, title, content, content_fingerprint, created_at, updated_at, revision, read_count, last_read_at)
            VALUES (:id, :title, :content, :fingerprint, :createdAt, :updatedAt, 1, 0, NULL)
            ON CONFLICT(content_fingerprint) DO NOTHING
            """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("id") id: String,
        @Param("title") title: String,
        @Param("content") content: String,
        @Param("fingerprint") fingerprint: String,
        @Param("createdAt") createdAt: String,
        @Param("updatedAt") updatedAt: String,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value =
            """
            UPDATE skills
            SET title = :title,
                content = :content,
                content_fingerprint = :fingerprint,
                updated_at = :updatedAt,
                revision = revision + 1
            WHERE id = :id
              AND revision = :expectedRevision
              AND NOT EXISTS (
                  SELECT 1 FROM skills duplicate
                  WHERE duplicate.content_fingerprint = :fingerprint
                    AND duplicate.id <> :id
              )
            """,
        nativeQuery = true,
    )
    fun updateIfRevisionMatches(
        @Param("id") id: String,
        @Param("expectedRevision") expectedRevision: Long,
        @Param("title") title: String,
        @Param("content") content: String,
        @Param("fingerprint") fingerprint: String,
        @Param("updatedAt") updatedAt: String,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "DELETE FROM SkillEntity skill WHERE skill.id = :id AND skill.revision = :expectedRevision",
    )
    fun deleteIfRevisionMatches(
        @Param("id") id: String,
        @Param("expectedRevision") expectedRevision: Long,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE SkillEntity skill
        SET skill.readCount = skill.readCount + 1, skill.lastReadAt = :readAt
        WHERE skill.id = :id
        """,
    )
    fun recordRead(
        @Param("id") id: String,
        @Param("readAt") readAt: Instant,
    ): Int
}

internal interface SkillRepositoryCustom {
    fun searchFts(
        query: String,
        limit: Int,
    ): List<SkillSearchRow>

    fun searchLike(
        query: String,
        limit: Int,
    ): List<SkillSearchRow>
}

internal data class SkillSearchRow(
    val id: String,
    val snippet: String,
)

@Repository
internal class SkillRepositoryCustomImpl(
    private val entityManager: EntityManager,
) : SkillRepositoryCustom {
    override fun searchFts(
        query: String,
        limit: Int,
    ): List<SkillSearchRow> =
        entityManager
            .createNativeQuery(
                """
                SELECT fts.id, snippet(skills_fts, 2, '<mark>', '</mark>', '…', 32)
                FROM skills_fts fts
                JOIN skills skill ON skill.id = fts.id
                WHERE skills_fts MATCH :query
                ORDER BY bm25(skills_fts, 5.0, 1.0), skill.updated_at DESC, skill.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("query", query)
            .setParameter("limit", limit)
            .resultList
            .map { row ->
                val values = row as Array<*>
                SkillSearchRow(values[0].toString(), values[1]?.toString().orEmpty())
            }

    override fun searchLike(
        query: String,
        limit: Int,
    ): List<SkillSearchRow> =
        entityManager
            .createNativeQuery(
                """
                SELECT id, substr(content, 1, 320)
                FROM skills
                WHERE lower(title) LIKE :query ESCAPE '\' OR lower(content) LIKE :query ESCAPE '\'
                ORDER BY updated_at DESC, id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("query", likePattern(query))
            .setParameter("limit", limit)
            .resultList
            .map { row ->
                val values = row as Array<*>
                SkillSearchRow(values[0].toString(), values[1]?.toString().orEmpty())
            }

    private fun likePattern(query: String): String =
        "%" +
            query
                .lowercase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") +
            "%"
}
