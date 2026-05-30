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
}
