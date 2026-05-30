package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.KnowledgeDb
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentManagerConversationPersistenceTest {
    @Test
    fun `conversation is persisted and reloaded on next manager instance`() {
        val tempDb = createTempDirectory("visual-agent-agent-history-test").resolve("history.db").toString()
        val db1 = KnowledgeDb(tempDb)
        val provider1 = mockk<LLMProvider>(relaxed = true)
        coEvery { provider1.chat(any<ChatRequestContext>()) } returns
            ChatResponse(
                model = "test",
                message = Message("assistant", "Saved response"),
                done = true,
            )
        val manager1 = AgentManager(db1, provider1, AgentToolConfigService(db1))

        kotlinx.coroutines.runBlocking {
            manager1.sendMessage("Persist me")
        }
        assertEquals(2, manager1.getHistory().size)
        db1.close()

        val db2 = KnowledgeDb(tempDb)
        val provider2 = mockk<LLMProvider>(relaxed = true)
        val manager2 = AgentManager(db2, provider2, AgentToolConfigService(db2))
        val loaded = manager2.getHistory()
        assertEquals(2, loaded.size)
        assertEquals("user", loaded[0].role)
        assertEquals("Persist me", loaded[0].content)
        assertEquals("assistant", loaded[1].role)
        assertEquals("Saved response", loaded[1].content)
        db2.close()
    }

    @Test
    fun `manager loads only latest 20 messages and can page older history`() {
        val tempDb = createTempDirectory("visual-agent-agent-history-page-test").resolve("history.db").toString()
        val db = KnowledgeDb(tempDb)
        repeat(30) { idx ->
            db.saveConversationMessage("main", if (idx % 2 == 0) "user" else "assistant", "message-$idx")
        }

        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db))
        val initial = manager.getHistory()
        assertEquals(20, initial.size)
        assertTrue(initial.first().content.contains("message-10"))
        assertTrue(initial.last().content.contains("message-29"))

        val older = manager.loadOlderHistory(20)
        assertEquals(10, older.size)
        assertTrue(older.first().content.contains("message-0"))
        assertTrue(older.last().content.contains("message-9"))
        assertEquals(30, manager.getHistory().size)
        db.close()
    }
}
