package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentManagerLoadLatestHistoryTest {
    @Test
    fun `loadLatestHistory appends newest db messages missing from in-memory history`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager =
            AgentManager(
                db,
                provider,
                AgentToolConfigService(db),
                ToolEventBus(),
                TodoEventBus(),
                AppConfigBean(db),
            )

        // Persist three messages directly to the DB (simulating a background process).
        db.saveConversationMessage("main", "user", "first", null)
        db.saveConversationMessage("main", "assistant", "second", null)
        db.saveConversationMessage("main", "user", "third", null)

        // Manager starts with empty in-memory history.
        assertEquals(0, manager.getHistory().size, "expected empty in-memory history at start")

        // Load the latest page should bring all three messages into memory.
        val loaded = manager.loadLatestHistory()
        assertEquals(3, loaded.size, "expected all three new messages to be loaded")
        assertEquals("third", loaded.last().content, "expected newest message content to be 'third'")

        val history = manager.getHistory()
        assertEquals(3, history.size, "expected in-memory history to contain three messages")
        assertEquals("third", history.last().content, "expected newest in-memory message to be 'third'")

        db.close()
    }

    @Test
    fun `loadLatestHistory does not duplicate messages already in memory`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager =
            AgentManager(
                db,
                provider,
                AgentToolConfigService(db),
                ToolEventBus(),
                TodoEventBus(),
                AppConfigBean(db),
            )

        // Persist an initial set of messages and load them.
        db.saveConversationMessage("main", "user", "first", null)
        db.saveConversationMessage("main", "assistant", "second", null)
        manager.loadLatestHistory()
        assertEquals(2, manager.getHistory().size, "expected two messages after initial load")

        // Persist a newer message directly to the DB.
        db.saveConversationMessage("main", "user", "third", null)

        // Reload the latest page: only the new message should be returned.
        val loaded = manager.loadLatestHistory()
        assertEquals(1, loaded.size, "expected only one new message")
        assertEquals("third", loaded.single().content, "expected newest message to be 'third'")

        val history = manager.getHistory()
        assertEquals(3, history.size, "expected history to grow by exactly one")
        assertEquals("third", history.last().content, "expected last in-memory message to be 'third'")

        // Calling it again with no new DB rows returns nothing and does not duplicate.
        val secondLoad = manager.loadLatestHistory()
        assertTrue(secondLoad.isEmpty(), "expected empty result when no new messages exist")
        assertEquals(3, manager.getHistory().size, "expected history size to remain unchanged")

        db.close()
    }
}
