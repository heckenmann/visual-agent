package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ConversationHistoryEntry
import de.heckenmann.visualagent.agent.tools.api.ConversationHistoryPort
import de.heckenmann.visualagent.knowledge.ConversationStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class HistoryToolTest {
    @Test
    fun `history tool loads and searches session history`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        db.saveConversationMessage("main", "user", "alpha one")
        db.saveConversationMessage("main", "assistant", "beta two")
        db.saveConversationMessage("main", "user", "gamma three")

        val tool = historyTool(db)
        val loadResult = tool.execute("""{"action":"load","limit":2,"offset":1}""", mapOf("sessionId" to "main"))
        assertTrue(loadResult.success)
        assertTrue(loadResult.content.contains("alpha one"))
        assertTrue(loadResult.content.contains("beta two"))

        val searchResult = tool.execute("""{"action":"search","query":"gamma"}""", mapOf("sessionId" to "main"))
        assertTrue(searchResult.success)
        assertTrue(searchResult.content.contains("gamma three"))
        db.close()
    }

    @Test
    fun `history tool rejects unsupported action`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val tool = historyTool(db)
        val result = tool.execute("""{"action":"unknown"}""", mapOf("sessionId" to "main"))
        assertFalse(result.success)
        db.close()
    }

    private fun historyTool(store: ConversationStore): HistoryTool =
        HistoryTool(
            object : ConversationHistoryPort {
                override fun loadPage(
                    sessionId: String,
                    limit: Int,
                    offset: Int,
                ): List<ConversationHistoryEntry> =
                    store.getConversationMessagesPage(sessionId, limit, offset).map {
                        ConversationHistoryEntry(it.createdAt, it.role, it.content)
                    }

                override fun search(
                    sessionId: String,
                    query: String,
                    limit: Int,
                ): List<ConversationHistoryEntry> =
                    store.searchConversationMessages(sessionId, query, limit).map {
                        ConversationHistoryEntry(it.createdAt, it.role, it.content)
                    }
            },
        )
}
