package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/** Persisted conversation message exposed to agent and tool consumers. */
data class ConversationRecord(
    val id: String,
    val role: String,
    val content: String,
    val metadata: String?,
    val createdAt: Instant,
    val timelineSequence: Long = 0,
    val contextPolicy: ConversationContextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
    val parentAssistantTurnId: String? = null,
    val turnOrder: Int? = null,
    val assistantToolTurn: Boolean = false,
    val conversationRequestId: String? = null,
) {
    /** Returns a field value by its persistence-facing name. */
    operator fun get(key: String): Any? =
        when (key) {
            "id" -> id
            "role" -> role
            "content" -> content
            "metadata" -> metadata
            "createdAt" -> createdAt.toString()
            "timelineSequence" -> timelineSequence
            "contextPolicy" -> contextPolicy.name
            "parentAssistantTurnId" -> parentAssistantTurnId
            "turnOrder" -> turnOrder
            "assistantToolTurn" -> assistantToolTurn
            "conversationRequestId" -> conversationRequestId
            else -> null
        }
}

/** A page whose records may include complete assistant/tool groups around the raw page boundary. */
data class ConversationStorePage(
    val records: List<ConversationRecord>,
    val nextOffset: Int,
    val hasMore: Boolean,
)

/** Stores, pages, searches, and deletes conversation messages. */
interface ConversationStore {
    /** Persists one conversation message using the caller-provided immutable identifier. */
    fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
    ): String = saveConversationMessage(id, sessionId, role, content, metadata, ConversationContextPolicy.forRole(role))

    /** Persists one message with an explicit model-context policy. */
    fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
        contextPolicy: ConversationContextPolicy,
        parentAssistantTurnId: String? = null,
        turnOrder: Int? = null,
        assistantToolTurn: Boolean = false,
        conversationRequestId: String? = null,
    ): String = saveConversationMessage(id, sessionId, role, content, metadata)

    /** Returns messages eligible for a bounded main-agent context projection. */
    fun getConversationMessagesForContext(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): List<ConversationRecord> =
        getConversationMessages(sessionId, recordLimit.coerceAtLeast(1)).let { rows ->
            val users = rows.filter { it.role == "user" }
            val boundary = users.takeLast(userTurnLimit.coerceAtLeast(1)).firstOrNull()?.timelineSequence
            rows.filter { row ->
                (boundary == null || row.timelineSequence >= boundary) && row.contextPolicy != ConversationContextPolicy.AUDIT_ONLY
            }
        }

    /** Returns one persisted message, including its durable timeline ordering key. */
    fun getConversationMessage(id: String): ConversationRecord? = null

    /** Returns every assistant message persisted for one conversation request in chronological order. */
    fun getConversationMessagesForRequest(requestId: String): List<ConversationRecord> = emptyList()

    /** Reactive counterpart of [getConversationMessagesForRequest]. */
    fun getConversationMessagesForRequestReactive(requestId: String): Flux<ConversationRecord> =
        Flux.defer { Flux.fromIterable(getConversationMessagesForRequest(requestId)) }

    /** Returns the latest messages for a session. */
    fun getConversationMessages(
        sessionId: String,
        limit: Int = 500,
    ): List<ConversationRecord>

    /** Returns one deterministic page of session messages. */
    fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationRecord>

    /** Reads a page and expands any assistant/tool groups touched by its raw rows. */
    fun getConversationHistoryPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): ConversationStorePage {
        val records = getConversationMessagesPage(sessionId, limit, offset)
        return ConversationStorePage(records, offset + records.size, records.size == limit)
    }

    /** Reads the latest page, expanding any assistant/tool groups it touches. */
    fun getLatestConversationHistoryPage(
        sessionId: String,
        limit: Int,
    ): ConversationStorePage {
        val records = getConversationMessages(sessionId, limit)
        return ConversationStorePage(records, records.size, records.size == limit)
    }

    /** Searches session messages by text query. */
    fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int = 20,
    ): List<ConversationRecord>

    /** Deletes all messages for a session and returns the affected count. */
    fun deleteConversationMessages(sessionId: String): Int

    /** Deletes a single message by id and returns the affected count. */
    fun deleteConversationMessageById(id: String): Int

    /** Updates the content of a single message by id and returns the affected count. */
    fun updateConversationMessageContent(
        id: String,
        newContent: String,
    ): Int

    /** Updates a persisted message's presentation content and metadata without changing its identity. */
    fun updateConversationMessage(
        id: String,
        newContent: String,
        newMetadata: String,
    ): Int = updateConversationMessageContent(id, newContent)

    /** Reactive counterpart of [saveConversationMessage]. */
    fun saveConversationMessageReactive(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
        contextPolicy: ConversationContextPolicy = ConversationContextPolicy.forRole(role),
        parentAssistantTurnId: String? = null,
        turnOrder: Int? = null,
        assistantToolTurn: Boolean = false,
        conversationRequestId: String? = null,
    ): Mono<String> =
        Mono.fromCallable {
            saveConversationMessage(
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
            )
        }

    /** Reactive counterpart of [getConversationMessage]. */
    fun getConversationMessageReactive(id: String): Mono<ConversationRecord> = Mono.fromCallable { getConversationMessage(id) }

    /** Reactive counterpart of [getConversationMessages]. */
    fun getConversationMessagesReactive(
        sessionId: String,
        limit: Int = 500,
    ): Flux<ConversationRecord> = Flux.defer { Flux.fromIterable(getConversationMessages(sessionId, limit)) }

    /** Reactive counterpart of [getConversationMessagesForContext]. */
    fun getConversationMessagesForContextReactive(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): Flux<ConversationRecord> = Flux.defer { Flux.fromIterable(getConversationMessagesForContext(sessionId, userTurnLimit, recordLimit)) }

    /** Reactive counterpart of [getConversationMessagesPage]. */
    fun getConversationMessagesPageReactive(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): Flux<ConversationRecord> = Flux.defer { Flux.fromIterable(getConversationMessagesPage(sessionId, limit, offset)) }

    /** Reactive counterpart of [searchConversationMessages]. */
    fun searchConversationMessagesReactive(
        sessionId: String,
        query: String,
        limit: Int = 20,
    ): Flux<ConversationRecord> = Flux.defer { Flux.fromIterable(searchConversationMessages(sessionId, query, limit)) }

    /** Reactive counterpart of [deleteConversationMessages]. */
    fun deleteConversationMessagesReactive(sessionId: String): Mono<Int> = Mono.fromCallable { deleteConversationMessages(sessionId) }

    /** Reactive counterpart of [deleteConversationMessageById]. */
    fun deleteConversationMessageByIdReactive(id: String): Mono<Int> = Mono.fromCallable { deleteConversationMessageById(id) }

    /** Reactive counterpart of [updateConversationMessageContent]. */
    fun updateConversationMessageContentReactive(
        id: String,
        newContent: String,
    ): Mono<Int> = Mono.fromCallable { updateConversationMessageContent(id, newContent) }

    /** Reactive counterpart of [updateConversationMessage]. */
    fun updateConversationMessageReactive(
        id: String,
        newContent: String,
        newMetadata: String,
    ): Mono<Int> = Mono.fromCallable { updateConversationMessage(id, newContent, newMetadata) }
}
