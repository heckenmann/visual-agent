package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockList
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockNullable
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockRequired
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/** R2DBC implementation for durable conversation history and bounded search. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcConversationStore(
    private val databaseClient: DatabaseClient,
    private val sequenceStore: R2dbcTimelineSequenceStore,
    private val transactionOperator: TransactionalOperator,
) : ConversationStore {
    override fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
    ): String = saveConversationMessageReactive(id, sessionId, role, content, metadata).blockRequired()

    override fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
        contextPolicy: ConversationContextPolicy,
    ): String = saveConversationMessageReactive(id, sessionId, role, content, metadata, contextPolicy).blockRequired()

    override fun getConversationMessage(id: String): ConversationRecord? = getConversationMessageReactive(id).blockNullable()

    override fun getConversationMessages(
        sessionId: String,
        limit: Int,
    ): List<ConversationRecord> = getConversationMessagesReactive(sessionId, limit).blockList()

    override fun getConversationMessagesForContext(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): List<ConversationRecord> = getConversationMessagesForContextReactive(sessionId, userTurnLimit, recordLimit).blockList()

    override fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationRecord> = getConversationMessagesPageReactive(sessionId, limit, offset).blockList()

    override fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationRecord> = searchConversationMessagesReactive(sessionId, query, limit).blockList()

    override fun deleteConversationMessages(sessionId: String): Int = deleteConversationMessagesReactive(sessionId).blockRequired()

    override fun deleteConversationMessageById(id: String): Int = deleteConversationMessageByIdReactive(id).blockRequired()

    override fun updateConversationMessageContent(
        id: String,
        newContent: String,
    ): Int = updateConversationMessageContentReactive(id, newContent).blockRequired()

    override fun saveConversationMessageReactive(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
        contextPolicy: ConversationContextPolicy,
    ): Mono<String> {
        require(UUID.fromString(id).toString() == id) { "Conversation message ID must be a canonical UUID" }
        return transactionOperator.transactional(
            existingIdentity(id)
                .flatMap { existing ->
                    require(
                        existing.sessionId == sessionId &&
                            existing.role == role &&
                            existing.content == content &&
                            existing.metadata == metadata &&
                            existing.contextPolicy == contextPolicy,
                    ) { "Conversation message $id conflicts with an existing entry" }
                    Mono.just(id)
                }.switchIfEmpty(
                    sequenceStore.nextReactive().flatMap { sequence ->
                        var statement =
                            databaseClient
                                .sql(
                                    """
                                    INSERT INTO conversation_history
                                        (id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy)
                                    VALUES (:id, :sessionId, :role, :content, :metadata, :createdAt, :timelineSequence, :contextPolicy)
                                    """.trimIndent(),
                                ).bind("id", id)
                                .bind("sessionId", sessionId)
                                .bind("role", role)
                                .bind("content", content)
                        statement = R2dbcPersistenceSupport.bindText(statement, "metadata", metadata)
                        statement
                            .bind("createdAt", Instant.now().toString())
                            .bind("timelineSequence", sequence)
                            .bind("contextPolicy", contextPolicy.name)
                            .fetch()
                            .rowsUpdated()
                            .thenReturn(id)
                    },
                ),
        )
    }

    override fun getConversationMessageReactive(id: String): Mono<ConversationRecord> = existingMessage(id)

    override fun getConversationMessagesReactive(
        sessionId: String,
        limit: Int,
    ): Flux<ConversationRecord> =
        databaseClient
            .sql(
                """
                SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy
                FROM conversation_history
                WHERE session_id = :sessionId
                ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                LIMIT :limit
                """.trimIndent(),
            ).bind("sessionId", sessionId)
            .bind("limit", limit.coerceAtLeast(1))
            .map { row, _ -> row.toConversationRecord() }
            .all()
            .collectList()
            .flatMapMany { Flux.fromIterable(it.asReversed()) }

    override fun getConversationMessagesForContextReactive(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): Flux<ConversationRecord> {
        val turnLimit = userTurnLimit.coerceAtLeast(1)
        val maxRecords = recordLimit.coerceAtLeast(1)
        val dialogue =
            databaseClient
                .sql(
                    """
                    WITH boundary AS (
                        SELECT timeline_sequence, created_at, id
                        FROM conversation_history
                        WHERE session_id = :sessionId AND role = 'user'
                        ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                        LIMIT 1 OFFSET :boundaryOffset
                    )
                    SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy
                    FROM conversation_history
                    WHERE session_id = :sessionId AND context_policy = 'DIALOGUE'
                      AND (
                          NOT EXISTS (SELECT 1 FROM boundary)
                          OR timeline_sequence > (SELECT timeline_sequence FROM boundary)
                          OR (
                              timeline_sequence = (SELECT timeline_sequence FROM boundary)
                              AND (
                                  created_at > (SELECT created_at FROM boundary)
                                  OR (
                                      created_at = (SELECT created_at FROM boundary)
                                      AND id >= (SELECT id FROM boundary)
                                  )
                              )
                          )
                      )
                    ORDER BY timeline_sequence ASC, created_at ASC, id ASC
                    LIMIT :recordLimit
                    """.trimIndent(),
                ).bind("sessionId", sessionId)
                .bind("boundaryOffset", turnLimit - 1)
                .bind("recordLimit", maxRecords)
                .map { row, _ -> row.toConversationRecord() }
                .all()
        val summaries =
            databaseClient
                .sql(
                    """
                    WITH previous_user AS (
                        SELECT timeline_sequence, created_at, id
                        FROM conversation_history
                        WHERE session_id = :sessionId AND role = 'user'
                        ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                        LIMIT 1 OFFSET :turnLimit
                    )
                    SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy
                    FROM conversation_history
                    WHERE session_id = :sessionId AND context_policy = 'SUMMARY_SOURCE'
                      AND (
                          NOT EXISTS (SELECT 1 FROM previous_user)
                          OR timeline_sequence > (SELECT timeline_sequence FROM previous_user)
                          OR (
                              timeline_sequence = (SELECT timeline_sequence FROM previous_user)
                              AND (
                                  created_at > (SELECT created_at FROM previous_user)
                                  OR (
                                      created_at = (SELECT created_at FROM previous_user)
                                      AND id > (SELECT id FROM previous_user)
                                  )
                              )
                          )
                      )
                    ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                    LIMIT :recordLimit
                    """.trimIndent(),
                ).bind("sessionId", sessionId)
                .bind("turnLimit", turnLimit)
                .bind("recordLimit", maxRecords)
                .map { row, _ -> row.toConversationRecord() }
                .all()
        return Flux
            .concat(dialogue, summaries)
            .collectList()
            .flatMapMany { rows ->
                Flux.fromIterable(
                    rows
                        .distinctBy(ConversationRecord::id)
                        .sortedWith(compareBy<ConversationRecord> { it.timelineSequence }.thenBy { it.createdAt }.thenBy { it.id }),
                )
            }
    }

    override fun getConversationMessagesPageReactive(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): Flux<ConversationRecord> =
        databaseClient
            .sql(
                """
                SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy
                FROM conversation_history
                WHERE session_id = :sessionId
                ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """.trimIndent(),
            ).bind("sessionId", sessionId)
            .bind("limit", limit.coerceAtLeast(1))
            .bind("offset", offset.coerceAtLeast(0))
            .map { row, _ -> row.toConversationRecord() }
            .all()
            .collectList()
            .flatMapMany { Flux.fromIterable(it.asReversed()) }

    override fun searchConversationMessagesReactive(
        sessionId: String,
        query: String,
        limit: Int,
    ): Flux<ConversationRecord> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return Flux.empty()
        return databaseClient
            .sql(
                """
                SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy
                FROM conversation_history
                WHERE session_id = :sessionId AND lower(content) LIKE :query
                ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                LIMIT :limit
                """.trimIndent(),
            ).bind("sessionId", sessionId)
            .bind("query", "%${normalized.lowercase()}%")
            .bind("limit", limit.coerceIn(1, 200))
            .map { row, _ -> row.toConversationRecord() }
            .all()
    }

    override fun deleteConversationMessagesReactive(sessionId: String): Mono<Int> =
        databaseClient
            .sql("DELETE FROM conversation_history WHERE session_id = :sessionId")
            .bind("sessionId", sessionId)
            .fetch()
            .rowsUpdated()
            .map(Long::toInt)

    override fun deleteConversationMessageByIdReactive(id: String): Mono<Int> =
        databaseClient
            .sql("DELETE FROM conversation_history WHERE id = :id")
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .map(Long::toInt)

    override fun updateConversationMessageContentReactive(
        id: String,
        newContent: String,
    ): Mono<Int> =
        databaseClient
            .sql("UPDATE conversation_history SET content = :content WHERE id = :id")
            .bind("content", newContent)
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .map(Long::toInt)

    private fun existingMessage(id: String): Mono<ConversationRecord> =
        databaseClient
            .sql(
                "SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy FROM conversation_history WHERE id = :id",
            ).bind("id", id)
            .map { row, _ -> row.toConversationRecord() }
            .one()

    private fun existingIdentity(id: String): Mono<ConversationIdentity> =
        databaseClient
            .sql("SELECT session_id, role, content, metadata, context_policy FROM conversation_history WHERE id = :id")
            .bind("id", id)
            .map { row, _ -> row.toConversationIdentity() }
            .one()
}
