package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
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

/** R2DBC implementation for durable conversation writes, using dedicated history query operations for reads. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcConversationStore(
    private val databaseClient: DatabaseClient,
    private val sequenceStore: R2dbcTimelineSequenceStore,
    private val historyQueries: R2dbcConversationHistoryQueries,
    private val transactionOperator: TransactionalOperator,
) : ConversationStore {
    override fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
    ): String =
        saveConversationMessageReactive(
            id,
            sessionId,
            role,
            content,
            metadata,
            ConversationContextPolicy.forRole(role),
        ).blockRequired()

    override fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
        contextPolicy: ConversationContextPolicy,
        parentAssistantTurnId: String?,
        turnOrder: Int?,
        assistantToolTurn: Boolean,
        conversationRequestId: String?,
    ): String =
        saveConversationMessageReactive(
            id,
            sessionId,
            role,
            content,
            metadata,
            contextPolicy,
            parentAssistantTurnId,
            turnOrder,
            assistantToolTurn,
            conversationRequestId,
        ).blockRequired()

    override fun getConversationMessage(id: String): ConversationRecord? = getConversationMessageReactive(id).blockNullable()

    override fun getConversationMessagesForRequest(requestId: String): List<ConversationRecord> =
        historyQueries.getConversationMessagesForRequest(requestId)

    override fun getConversationMessagesForRequestReactive(requestId: String): Flux<ConversationRecord> =
        historyQueries.getConversationMessagesForRequestReactive(requestId)

    override fun getConversationMessages(
        sessionId: String,
        limit: Int,
    ): List<ConversationRecord> = historyQueries.getConversationMessages(sessionId, limit)

    override fun getConversationMessagesReactive(
        sessionId: String,
        limit: Int,
    ): Flux<ConversationRecord> = historyQueries.getConversationMessagesReactive(sessionId, limit)

    override fun getConversationMessagesForContext(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): List<ConversationRecord> = historyQueries.getConversationMessagesForContext(sessionId, userTurnLimit, recordLimit)

    override fun getConversationMessagesForContextReactive(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): Flux<ConversationRecord> = historyQueries.getConversationMessagesForContextReactive(sessionId, userTurnLimit, recordLimit)

    override fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationRecord> = historyQueries.getConversationMessagesPage(sessionId, limit, offset)

    override fun getConversationMessagesPageReactive(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): Flux<ConversationRecord> = historyQueries.getConversationMessagesPageReactive(sessionId, limit, offset)

    override fun getConversationHistoryPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): ConversationStorePage = historyQueries.getConversationHistoryPage(sessionId, limit, offset)

    override fun getLatestConversationHistoryPage(
        sessionId: String,
        limit: Int,
    ): ConversationStorePage = historyQueries.getLatestConversationHistoryPage(sessionId, limit)

    override fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationRecord> = historyQueries.searchConversationMessages(sessionId, query, limit)

    override fun searchConversationMessagesReactive(
        sessionId: String,
        query: String,
        limit: Int,
    ): Flux<ConversationRecord> = historyQueries.searchConversationMessagesReactive(sessionId, query, limit)

    override fun deleteConversationMessages(sessionId: String): Int = deleteConversationMessagesReactive(sessionId).blockRequired()

    override fun deleteConversationMessageById(id: String): Int = deleteConversationMessageByIdReactive(id).blockRequired()

    override fun updateConversationMessageContent(
        id: String,
        newContent: String,
    ): Int = updateConversationMessageContentReactive(id, newContent).blockRequired()

    override fun updateConversationMessage(
        id: String,
        newContent: String,
        newMetadata: String,
    ): Int = updateConversationMessageReactive(id, newContent, newMetadata).blockRequired()

    override fun saveConversationMessageReactive(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
        contextPolicy: ConversationContextPolicy,
        parentAssistantTurnId: String?,
        turnOrder: Int?,
        assistantToolTurn: Boolean,
        conversationRequestId: String?,
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
                            existing.contextPolicy == contextPolicy &&
                            existing.parentAssistantTurnId == parentAssistantTurnId &&
                            existing.turnOrder == turnOrder &&
                            existing.assistantToolTurn == assistantToolTurn &&
                            existing.conversationRequestId == conversationRequestId,
                    ) { "Conversation message $id conflicts with an existing entry" }
                    Mono.just(id)
                }.switchIfEmpty(
                    sequenceStore.nextReactive().flatMap { sequence ->
                        var statement =
                            databaseClient
                                .sql(
                                    """
                                    INSERT INTO conversation_history
                                        (id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy,
                                         parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id)
                                    VALUES (:id, :sessionId, :role, :content, :metadata, :createdAt, :timelineSequence, :contextPolicy,
                                            :parentAssistantTurnId, :turnOrder, :assistantToolTurn, :conversationRequestId)
                                    """.trimIndent(),
                                ).bind("id", id)
                                .bind("sessionId", sessionId)
                                .bind("role", role)
                                .bind("content", content)
                        statement = R2dbcPersistenceSupport.bindText(statement, "metadata", metadata)
                        statement = R2dbcPersistenceSupport.bindText(statement, "parentAssistantTurnId", parentAssistantTurnId)
                        statement = R2dbcPersistenceSupport.bindText(statement, "conversationRequestId", conversationRequestId)
                        statement =
                            if (turnOrder == null) {
                                statement.bindNull("turnOrder", Int::class.java)
                            } else {
                                statement.bind("turnOrder", turnOrder)
                            }
                        statement
                            .bind("createdAt", Instant.now().toString())
                            .bind("timelineSequence", sequence)
                            .bind("contextPolicy", contextPolicy.name)
                            .bind("assistantToolTurn", assistantToolTurn)
                            .fetch()
                            .rowsUpdated()
                            .thenReturn(id)
                    },
                ),
        )
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

    override fun updateConversationMessageReactive(
        id: String,
        newContent: String,
        newMetadata: String,
    ): Mono<Int> =
        databaseClient
            .sql("UPDATE conversation_history SET content = :content, metadata = :metadata WHERE id = :id")
            .bind("content", newContent)
            .bind("metadata", newMetadata)
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .map(Long::toInt)

    override fun getConversationMessageReactive(id: String): Mono<ConversationRecord> = existingMessage(id)

    private fun existingMessage(id: String): Mono<ConversationRecord> =
        databaseClient
            .sql(
                "SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy, parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id FROM conversation_history WHERE id = :id",
            ).bind("id", id)
            .map { row, _ -> row.toConversationRecord() }
            .one()

    private fun existingIdentity(id: String): Mono<ConversationIdentity> =
        databaseClient
            .sql(
                "SELECT session_id, role, content, metadata, context_policy, parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id FROM conversation_history WHERE id = :id",
            ).bind("id", id)
            .map { row, _ -> row.toConversationIdentity() }
            .one()
}
