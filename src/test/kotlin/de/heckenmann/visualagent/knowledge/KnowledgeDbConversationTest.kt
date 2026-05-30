package de.heckenmann.visualagent.knowledge

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnowledgeDbConversationTest {
    @Test
    fun `save load and delete conversation messages`() {
        val tempDb = createTempDirectory("visual-agent-conversation-db-test").resolve("history.db").toString()
        val db = KnowledgeDb(tempDb)
        val sessionId = "main"

        db.saveConversationMessage(sessionId, "user", "Hello")
        db.saveConversationMessage(sessionId, "assistant", "Hi there")
        db.saveConversationMessage("other", "user", "Ignore me")

        val messages = db.getConversationMessages(sessionId)
        assertEquals(2, messages.size)
        assertEquals("user", messages[0]["role"])
        assertEquals("Hello", messages[0]["content"])
        assertEquals("assistant", messages[1]["role"])
        assertEquals("Hi there", messages[1]["content"])

        val deleted = db.deleteConversationMessages(sessionId)
        assertTrue(deleted >= 2)
        assertEquals(0, db.getConversationMessages(sessionId).size)
        assertEquals(1, db.getConversationMessages("other").size)
        db.close()
    }

    @Test
    fun `conversation supports paging and keyword search`() {
        val tempDb = createTempDirectory("visual-agent-conversation-page-test").resolve("history.db").toString()
        val db = KnowledgeDb(tempDb)
        val sessionId = "main"
        repeat(10) { idx ->
            db.saveConversationMessage(sessionId, if (idx % 2 == 0) "user" else "assistant", "entry-$idx keyword")
        }

        val latestPage = db.getConversationMessagesPage(sessionId, limit = 3, offset = 0)
        assertEquals(3, latestPage.size)
        assertEquals("entry-7 keyword", latestPage[0]["content"])
        assertEquals("entry-9 keyword", latestPage[2]["content"])

        val olderPage = db.getConversationMessagesPage(sessionId, limit = 4, offset = 6)
        assertEquals(4, olderPage.size)
        assertEquals("entry-0 keyword", olderPage[0]["content"])
        assertEquals("entry-3 keyword", olderPage[3]["content"])

        val matches = db.searchConversationMessages(sessionId, "entry-8", limit = 5)
        assertEquals(1, matches.size)
        assertEquals("entry-8 keyword", matches[0]["content"])
        db.close()
    }
}
