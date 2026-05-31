package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentManagerConversationPersistenceTest {
    @Test
    fun `conversation is persisted and reloaded on next manager instance`() {
        val tempDb = createTempDirectory("visual-agent-agent-history-test").resolve("history.db").toString()
        val db1 =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider1 = mockk<LLMProvider>(relaxed = true)
        coEvery { provider1.chat(any<ChatRequestContext>()) } returns
            ChatResponse(
                model = "test",
                message = Message("assistant", "Saved response"),
                done = true,
            )
        val manager1 = AgentManager(db1, provider1, AgentToolConfigService(db1), ToolEventBus())

        kotlinx.coroutines.runBlocking {
            manager1.sendMessage("Persist me")
        }
        assertEquals(2, manager1.getHistory().size)
        db1.close()

        val db2 =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider2 = mockk<LLMProvider>(relaxed = true)
        val manager2 = AgentManager(db2, provider2, AgentToolConfigService(db2), ToolEventBus())
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
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        repeat(30) { idx ->
            db.saveConversationMessage("main", if (idx % 2 == 0) "user" else "assistant", "message-$idx")
        }

        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
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

    @Test
    fun `clear then welcome persists greeting message`() {
        val tempDb = createTempDirectory("visual-agent-agent-welcome-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider = mockk<LLMProvider>(relaxed = true)
        coEvery { provider.chat(any<ChatRequestContext>()) } returns
            ChatResponse(
                model = "test",
                message = Message("assistant", "Hello, I can help with files, todos, code, terminal, and project context."),
                done = true,
            )
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())

        manager.clearHistory()
        val welcome =
            kotlinx.coroutines.runBlocking {
                manager.addWelcomeMessageAfterReset()
            }

        val history = manager.getHistory()
        assertEquals(1, history.size)
        assertEquals("assistant", history[0].role)
        assertEquals(welcome, history[0].content)

        val rows = db.getConversationMessages("main", 20)
        assertEquals(1, rows.size)
        assertEquals("assistant", rows[0]["role"])
        assertEquals(welcome, rows[0]["content"])
        db.close()
    }

    @Test
    fun `runaway repetition response is sanitized before persisting`() {
        val tempDb = createTempDirectory("visual-agent-agent-repetition-guard-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider = mockk<LLMProvider>(relaxed = true)
        val repeated = List(80) { "Meine letzte Nachricht war:" }.joinToString(" ")
        coEvery { provider.chat(any<ChatRequestContext>()) } returnsMany
            listOf(
                ChatResponse(
                    model = "test",
                    message = Message("assistant", repeated),
                    done = true,
                ),
                ChatResponse(
                    model = "test",
                    message = Message("assistant", "Hier ist die korrigierte, kurze Antwort ohne Wiederholung."),
                    done = true,
                ),
            )
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())

        val answer =
            kotlinx.coroutines.runBlocking {
                manager.sendMessage("test")
            }

        assertTrue(answer.contains("korrigierte"))
        val rows = db.getConversationMessages("main", 20)
        val last = rows.last()
        assertEquals("assistant", last["role"])
        assertTrue(last["content"]?.contains("korrigierte") == true)
        coVerify(exactly = 2) { provider.chat(any<ChatRequestContext>()) }
        db.close()
    }
}
