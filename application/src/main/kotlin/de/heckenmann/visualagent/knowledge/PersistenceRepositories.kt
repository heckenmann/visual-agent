package de.heckenmann.visualagent.knowledge

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

internal interface MemoryProjection {
    val id: String
    val content: String
    val tags: String?
    val createdAt: Instant
}

internal interface MemoryRepository : JpaRepository<MemoryEntity, String> {
    @Query(
        """
        SELECT memory.id AS id, memory.content AS content, memory.tags AS tags, memory.createdAt AS createdAt,
               memory.accessCount AS accessCount, memory.lastAccessed AS lastAccessed
        FROM MemoryEntity memory
        WHERE memory.content LIKE CONCAT('%', :query, '%')
           OR memory.tags LIKE CONCAT('%', :query, '%')
        ORDER BY memory.createdAt DESC
        """,
    )
    fun search(
        @Param("query") query: String,
        pageable: Pageable,
    ): List<MemoryProjection>
}

internal interface ProjectKnowledgeRepository : JpaRepository<ProjectKnowledgeEntity, String>

internal interface PreferenceRepository : JpaRepository<PreferenceEntity, String>

internal interface ConversationRepository :
    JpaRepository<ConversationEntity, String>,
    ConversationRepositoryCustom {
    fun findBySessionIdOrderByTimelineSequenceDescCreatedAtDescIdDesc(
        sessionId: String,
        pageable: Pageable,
    ): List<ConversationEntity>

    @Modifying
    @Query("DELETE FROM ConversationEntity message WHERE message.sessionId = :sessionId")
    fun deleteBySessionId(
        @Param("sessionId") sessionId: String,
    ): Int
}

internal interface ConversationRepositoryCustom {
    fun findPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationEntity>

    fun searchFts(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationEntity>

    fun searchLike(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationEntity>

    fun findForMainContext(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): List<ConversationEntity>
}

@Repository
internal class ConversationRepositoryCustomImpl(
    private val entityManager: EntityManager,
) : ConversationRepositoryCustom {
    override fun findPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationEntity> =
        entityManager
            .createQuery(
                """
                SELECT message FROM ConversationEntity message
                WHERE message.sessionId = :sessionId
                ORDER BY message.timelineSequence DESC, message.createdAt DESC, message.id DESC
                """.trimIndent(),
                ConversationEntity::class.java,
            ).setParameter("sessionId", sessionId)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .resultList

    override fun searchFts(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationEntity> =
        entityManager
            .createNativeQuery(FTS_QUERY, ConversationEntity::class.java)
            .setParameter("sessionId", sessionId)
            .setParameter("query", query)
            .setMaxResults(limit)
            .resultList
            .filterIsInstance<ConversationEntity>()

    override fun searchLike(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationEntity> =
        entityManager
            .createQuery(
                """
                SELECT message FROM ConversationEntity message
                WHERE message.sessionId = :sessionId
                  AND lower(message.content) LIKE :query
                ORDER BY message.timelineSequence DESC, message.createdAt DESC, message.id DESC
                """.trimIndent(),
                ConversationEntity::class.java,
            ).setParameter("sessionId", sessionId)
            .setParameter("query", "%${query.lowercase()}%")
            .setMaxResults(limit)
            .resultList

    override fun findForMainContext(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): List<ConversationEntity> {
        val turnLimit = userTurnLimit.coerceAtLeast(1)
        val maxRecords = recordLimit.coerceAtLeast(1)
        val boundary =
            entityManager
                .createNativeQuery(
                    """
                    SELECT MIN(timeline_sequence)
                    FROM (
                        SELECT timeline_sequence
                        FROM conversation_history
                        WHERE session_id = :sessionId AND role = 'user'
                        ORDER BY timeline_sequence DESC
                        LIMIT :turnLimit
                    )
                    """.trimIndent(),
                ).setParameter("sessionId", sessionId)
                .setParameter("turnLimit", turnLimit)
                .singleResult
                ?.let { (it as? Number)?.toLong() }
                ?: return emptyList()
        val dialogue =
            entityManager
                .createNativeQuery(
                    """
                    SELECT *
                    FROM conversation_history
                    WHERE session_id = :sessionId
                      AND timeline_sequence >= :boundary
                      AND context_policy = 'DIALOGUE'
                    ORDER BY timeline_sequence ASC, created_at ASC, id ASC
                    """.trimIndent(),
                    ConversationEntity::class.java,
                ).setParameter("sessionId", sessionId)
                .setParameter("boundary", boundary)
                .resultList
                .filterIsInstance<ConversationEntity>()
        val summaries =
            entityManager
                .createNativeQuery(
                    """
                    SELECT *
                    FROM conversation_history
                    WHERE session_id = :sessionId
                      AND timeline_sequence >= :boundary
                      AND context_policy = 'SUMMARY_SOURCE'
                    ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                    LIMIT :recordLimit
                    """.trimIndent(),
                    ConversationEntity::class.java,
                ).setParameter("sessionId", sessionId)
                .setParameter("boundary", boundary)
                .setParameter("recordLimit", maxRecords)
                .resultList
                .filterIsInstance<ConversationEntity>()
        return (dialogue + summaries)
            .distinctBy(ConversationEntity::id)
            .sortedWith(compareBy<ConversationEntity> { it.timelineSequence }.thenBy { it.createdAt }.thenBy { it.id })
    }

    private companion object {
        private const val FTS_QUERY =
            """
            SELECT ch.*
            FROM conversation_history_fts fts
            JOIN conversation_history ch ON ch.id = fts.id
            WHERE fts.session_id = :sessionId AND fts.content MATCH :query
            ORDER BY ch.timeline_sequence DESC, ch.created_at DESC, ch.id DESC
            """
    }
}

internal interface WorkspaceFileRepository : JpaRepository<WorkspaceFileEntity, String> {
    fun findAllByOrderByImportedAtDescIdDesc(): List<WorkspaceFileEntity>

    fun findByRelativePath(relativePath: String): WorkspaceFileEntity?
}

internal interface TodoRepository : JpaRepository<TodoEntity, String> {
    fun findAllByOrderByPositionAscIdAsc(): List<TodoEntity>
}

internal interface DeletedTodoRepository : JpaRepository<DeletedTodoEntity, String> {
    fun findAllByOrderByUpdatedAtDescIdDesc(pageable: org.springframework.data.domain.Pageable): List<DeletedTodoEntity>
}

internal interface SubAgentRepository : JpaRepository<SubAgentEntity, String> {
    fun findAllByOrderByCreatedAtDescIdDesc(): List<SubAgentEntity>

    fun findByStatusOrderByCreatedAtDescIdDesc(status: String): List<SubAgentEntity>
}

internal interface SubAgentConfigRepository : JpaRepository<SubAgentConfigEntity, String> {
    fun findAllByOrderByIdAsc(): List<SubAgentConfigEntity>
}
