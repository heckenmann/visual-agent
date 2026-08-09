package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.Message

/**
 * Immutable page of persisted conversation messages.
 *
 * @property messages Messages in chronological order
 * @property offset Database offset represented by this page
 * @property hasMore Whether another older page may exist
 */
data class ConversationHistoryPage(
    val messages: List<Message>,
    val offset: Int,
    val hasMore: Boolean,
)
