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
            else -> null
        }
}

/** Stores, pages, searches, and deletes conversation messages. */
interface ConversationStore {
    /** Persists one conversation message using the caller-provided immutable identifier. */
    fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
    ): String

    /** Persists one message with an explicit model-context policy. */
    fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
        contextPolicy: ConversationContextPolicy,
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

    /** Reactive counterpart of [saveConversationMessage]. */
    fun saveConversationMessageReactive(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
        contextPolicy: ConversationContextPolicy = ConversationContextPolicy.forRole(role),
    ): Mono<String> = Mono.fromCallable { saveConversationMessage(id, sessionId, role, content, metadata, contextPolicy) }

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
}
