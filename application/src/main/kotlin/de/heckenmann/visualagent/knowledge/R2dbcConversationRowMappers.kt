package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import io.r2dbc.spi.Row
import java.time.Instant

/** Identity fields used to validate idempotent conversation inserts. */
internal data class ConversationIdentity(
    val sessionId: String,
    val role: String,
    val content: String,
    val metadata: String?,
    val contextPolicy: ConversationContextPolicy,
    val parentAssistantTurnId: String?,
    val turnOrder: Int?,
    val assistantToolTurn: Boolean,
    val conversationRequestId: String?,
)

/** Maps the identity columns used by idempotent conversation inserts. */
internal fun Row.toConversationIdentity(): ConversationIdentity =
    ConversationIdentity(
        sessionId = R2dbcPersistenceSupport.requiredText(this, "session_id"),
        role = R2dbcPersistenceSupport.requiredText(this, "role"),
        content = R2dbcPersistenceSupport.requiredText(this, "content"),
        metadata = R2dbcPersistenceSupport.text(this, "metadata"),
        contextPolicy =
            runCatching { ConversationContextPolicy.valueOf(R2dbcPersistenceSupport.requiredText(this, "context_policy")) }
                .getOrDefault(ConversationContextPolicy.SUMMARY_SOURCE),
        parentAssistantTurnId = R2dbcPersistenceSupport.text(this, "parent_assistant_turn_id"),
        turnOrder = R2dbcPersistenceSupport.integer(this, "turn_order"),
        assistantToolTurn = R2dbcPersistenceSupport.requiredText(this, "assistant_tool_turn").toBoolean(),
        conversationRequestId = R2dbcPersistenceSupport.text(this, "conversation_request_id"),
    )

/** Maps a conversation row into the persistence domain record. */
internal fun Row.toConversationRecord(): ConversationRecord =
    ConversationRecord(
        id = R2dbcPersistenceSupport.requiredText(this, "id"),
        role = R2dbcPersistenceSupport.requiredText(this, "role"),
        content = R2dbcPersistenceSupport.requiredText(this, "content"),
        metadata = R2dbcPersistenceSupport.text(this, "metadata"),
        createdAt = R2dbcPersistenceSupport.instant(this, "created_at") ?: Instant.EPOCH,
        timelineSequence = R2dbcPersistenceSupport.long(this, "timeline_sequence") ?: 0L,
        contextPolicy =
            runCatching { ConversationContextPolicy.valueOf(R2dbcPersistenceSupport.requiredText(this, "context_policy")) }
                .getOrDefault(ConversationContextPolicy.SUMMARY_SOURCE),
        parentAssistantTurnId = R2dbcPersistenceSupport.text(this, "parent_assistant_turn_id"),
        turnOrder = R2dbcPersistenceSupport.integer(this, "turn_order"),
        assistantToolTurn = R2dbcPersistenceSupport.requiredText(this, "assistant_tool_turn").toBoolean(),
        conversationRequestId = R2dbcPersistenceSupport.text(this, "conversation_request_id"),
    )
