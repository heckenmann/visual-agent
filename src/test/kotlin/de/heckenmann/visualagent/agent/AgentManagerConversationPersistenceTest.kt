package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.conversation.WelcomeResult
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
        val manager1 = AgentManager(db1, provider1, AgentToolConfigService(db1), ToolEventBus(), TodoEventBus())

        kotlinx.coroutines.runBlocking {
            manager1.sendMessage("Persist me")
        }
        assertEquals(2, manager1.getHistory().size)
        db1.close()

        val db2 =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider2 = mockk<LLMProvider>(relaxed = true)
        val manager2 = AgentManager(db2, provider2, AgentToolConfigService(db2), ToolEventBus(), TodoEventBus())
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
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
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
        coEvery { provider.checkConnection() } returns true
        coEvery { provider.getModels() } returns listOf("llava")
        coEvery { provider.chat(any<ChatRequestContext>()) } returns
            ChatResponse(
                model = "test",
                message = Message("assistant", "Hello, I can help with files, todos, code, terminal, and project context."),
                done = true,
            )
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())

        manager.clearHistory()
        val welcome =
            kotlinx.coroutines.runBlocking {
                (
                    manager.addWelcomeMessageAfterReset() as
                        WelcomeResult.Generated
                ).message
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
    fun `clear then welcome falls back to static greeting when provider chat fails`() {
        val tempDb = createTempDirectory("visual-agent-agent-welcome-fallback-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider = mockk<LLMProvider>(relaxed = true)
        coEvery { provider.checkConnection() } returns true
        coEvery { provider.getModels() } returns listOf("llava")
        coEvery { provider.chat(any<ChatRequestContext>()) } throws
            IllegalStateException("Provider timeout")
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())

        manager.clearHistory()
        val result =
            kotlinx.coroutines.runBlocking {
                manager.addWelcomeMessageAfterReset()
            }

        assertTrue(result is WelcomeResult.Fallback)
        val history = manager.getHistory()
        assertEquals(1, history.size)
        assertEquals("assistant", history[0].role)
        assertEquals(result.message, history[0].content)
        assertTrue(history[0].content.contains("Hello! I'm ready to help"))
        db.close()
    }

    @Test
    fun `welcome request includes user model instruction when configured`() {
        val tempDb = createTempDirectory("visual-agent-agent-welcome-instruction-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider = mockk<LLMProvider>(relaxed = true)
        coEvery { provider.checkConnection() } returns true
        coEvery { provider.getModels() } returns listOf("llava")
        val requestSlot = slot<ChatRequestContext>()
        coEvery { provider.chat(capture(requestSlot)) } returns
            ChatResponse(
                model = "test",
                message = Message("assistant", "Guten Tag!"),
                done = true,
            )
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
        val previousInstruction = de.heckenmann.visualagent.config.AppConfig.instance.userModelInstruction
        try {
            de.heckenmann.visualagent.config.AppConfig.instance.userModelInstruction = "Always answer in German."
            manager.clearHistory()
            val result =
                kotlinx.coroutines.runBlocking {
                    manager.addWelcomeMessageAfterReset()
                }

            assertTrue(result is WelcomeResult.Generated)
            val messages = requestSlot.captured.messages
            assertEquals(1, messages.size)
            assertEquals("system", messages[0].role)
            assertTrue(messages[0].content.contains("Greet the user after a conversation reset"))
            assertTrue(messages[0].content.contains("Always answer in German."))
        } finally {
            de.heckenmann.visualagent.config.AppConfig.instance.userModelInstruction = previousInstruction
        }
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
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())

        val answer =
            kotlinx.coroutines.runBlocking {
                manager.sendMessage("test")
            }

        assertTrue(answer.contains("korrigierte"))
        val rows = db.getConversationMessages("main", 20)
        val last = rows.last()
        assertEquals("assistant", last.role)
        assertTrue(last.content.contains("korrigierte"))
        coVerify(exactly = 2) { provider.chat(any<ChatRequestContext>()) }
        db.close()
    }
}
