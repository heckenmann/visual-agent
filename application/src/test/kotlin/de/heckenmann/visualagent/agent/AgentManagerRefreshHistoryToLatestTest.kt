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

class AgentManagerRefreshHistoryToLatestTest {
    @Test
    fun `refreshHistoryToLatest clears memory and loads latest db page`() {
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

        // Persist three messages and load all into memory.
        db.saveConversationMessage("main", "user", "first", null)
        db.saveConversationMessage("main", "assistant", "second", null)
        db.saveConversationMessage("main", "user", "third", null)
        manager.loadLatestHistory()
        assertEquals(3, manager.getHistory().size)

        // Persist several newer messages directly to the DB (background writes).
        (4..30).forEach { index ->
            db.saveConversationMessage("main", "user", "msg-$index", null)
        }

        // Refresh to latest: in-memory history is cleared and only the 20 newest are loaded.
        val refreshed = manager.refreshHistoryToLatest()
        assertEquals(20, refreshed.size, "expected latest page size (20) to be loaded")
        assertEquals(20, manager.getHistory().size, "expected in-memory history to contain exactly the latest page")
        assertEquals("msg-30", refreshed.last().content, "expected newest DB message to be last")
        assertEquals("msg-30", manager.getHistory().last().content, "expected newest DB message to be last in memory")

        db.close()
    }

    @Test
    fun `refreshHistoryToLatest does not duplicate in-memory messages`() {
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

        db.saveConversationMessage("main", "user", "first", null)
        db.saveConversationMessage("main", "assistant", "second", null)
        manager.refreshHistoryToLatest()

        val ids = manager.getHistory().map { it.id }.toSet()
        assertEquals(2, ids.size, "expected two unique messages")
        assertEquals(2, manager.getHistory().size, "expected no duplicates after refresh")

        db.close()
    }

    @Test
    fun `refreshHistoryToLatest returns empty list when history is empty`() {
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

        val refreshed = manager.refreshHistoryToLatest()
        assertTrue(refreshed.isEmpty(), "expected empty result when DB has no messages")
        assertTrue(manager.getHistory().isEmpty(), "expected empty in-memory history")

        db.close()
    }
}
