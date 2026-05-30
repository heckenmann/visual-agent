package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.knowledge.KnowledgeDb
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryToolTest {
    @Test
    fun `history tool loads and searches session history`() {
        val db = KnowledgeDb("jdbc:sqlite::memory:")
        db.saveConversationMessage("main", "user", "alpha one")
        db.saveConversationMessage("main", "assistant", "beta two")
        db.saveConversationMessage("main", "user", "gamma three")

        val tool = HistoryTool(db)
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
        val db = KnowledgeDb("jdbc:sqlite::memory:")
        val tool = HistoryTool(db)
        val result = tool.execute("""{"action":"unknown"}""", mapOf("sessionId" to "main"))
        assertFalse(result.success)
        db.close()
    }
}
