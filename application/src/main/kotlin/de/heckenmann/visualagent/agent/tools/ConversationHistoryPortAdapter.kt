package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ConversationHistoryEntry
import de.heckenmann.visualagent.agent.tools.api.ConversationHistoryPort
import de.heckenmann.visualagent.knowledge.ConversationStore
import org.springframework.stereotype.Component

/**
 * Adapts the Application persistence store to the provider-neutral history tool port.
 */
@Component
class ConversationHistoryPortAdapter(
    private val conversationStore: ConversationStore,
) : ConversationHistoryPort {
    override fun loadPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationHistoryEntry> =
        conversationStore.getConversationMessagesPage(sessionId, limit, offset).map(::toEntry)

    override fun search(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationHistoryEntry> =
        conversationStore.searchConversationMessages(sessionId, query, limit).map(::toEntry)

    private fun toEntry(record: de.heckenmann.visualagent.knowledge.ConversationRecord): ConversationHistoryEntry =
        ConversationHistoryEntry(record.createdAt, record.role, record.content)
}
