package de.heckenmann.visualagent.agent.tools.api

import java.time.Instant

/**
 * Provider-neutral access to persisted conversation history required by the history tool.
 */
interface ConversationHistoryPort {
    /** Loads one deterministic page of conversation messages. */
    fun loadPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationHistoryEntry>

    /** Searches conversation messages for a session. */
    fun search(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationHistoryEntry>
}

/** One conversation entry exposed to a tool without persistence-layer types. */
data class ConversationHistoryEntry(
    /** Message creation timestamp. */
    val createdAt: Instant,
    /** Message role. */
    val role: String,
    /** Message content. */
    val content: String,
)
