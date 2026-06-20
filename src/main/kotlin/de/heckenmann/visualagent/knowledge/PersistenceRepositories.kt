package de.heckenmann.visualagent.knowledge

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

internal interface MemoryRepository : JpaRepository<MemoryEntity, String> {
    @Query(
        """
        SELECT memory FROM MemoryEntity memory
        WHERE memory.content LIKE CONCAT('%', :query, '%')
           OR memory.tags LIKE CONCAT('%', :query, '%')
        ORDER BY memory.createdAt DESC
        """,
    )
    fun search(
        @Param("query") query: String,
        pageable: Pageable,
    ): List<MemoryEntity>
}

internal interface ProjectKnowledgeRepository : JpaRepository<ProjectKnowledgeEntity, String>

internal interface PreferenceRepository : JpaRepository<PreferenceEntity, String>

internal interface ConversationRepository :
    JpaRepository<ConversationEntity, String>,
    ConversationRepositoryCustom {
    fun findBySessionIdOrderByCreatedAtDescIdDesc(
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
                ORDER BY message.createdAt DESC, message.id DESC
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
                ORDER BY message.createdAt DESC, message.id DESC
                """.trimIndent(),
                ConversationEntity::class.java,
            ).setParameter("sessionId", sessionId)
            .setParameter("query", "%${query.lowercase()}%")
            .setMaxResults(limit)
            .resultList

    private companion object {
        private const val FTS_QUERY =
            """
            SELECT ch.*
            FROM conversation_history_fts fts
            JOIN conversation_history ch ON ch.id = fts.id
            WHERE fts.session_id = :sessionId AND fts.content MATCH :query
            ORDER BY ch.created_at DESC, ch.id DESC
            """
    }
}

internal interface WorkspaceFileRepository : JpaRepository<WorkspaceFileEntity, String> {
    fun findAllByOrderByImportedAtDescIdDesc(): List<WorkspaceFileEntity>

    fun findByRelativePath(relativePath: String): WorkspaceFileEntity?
}

internal interface TodoRepository : JpaRepository<TodoEntity, String> {
    fun findAllByOrderByCreatedAtAscIdAsc(): List<TodoEntity>
}

internal interface SubAgentRepository : JpaRepository<SubAgentEntity, String> {
    fun findAllByOrderByCreatedAtDescIdDesc(): List<SubAgentEntity>

    fun findByStatusOrderByCreatedAtDescIdDesc(status: String): List<SubAgentEntity>
}

internal interface SubAgentConfigRepository : JpaRepository<SubAgentConfigEntity, String> {
    fun findAllByOrderByIdAsc(): List<SubAgentConfigEntity>
}
