package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentManagerConversationOpsTest {
    @Test
    fun `clear history removes all messages from memory and store`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        manager.appendSystemMessage("context")

        manager.clearHistory()

        assertEquals(0, manager.getHistory().size)
        assertEquals(0, db.getConversationMessages("main", 100).size)
    }

    @Test
    fun `delete message by id removes it from memory and store`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        manager.appendSystemMessage("first")
        val id = db.saveConversationMessage("main", "user", "second")
        val manager2 = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())

        manager2.deleteMessageById(id)

        assertTrue(manager2.getHistory().none { it.id == id })
        assertTrue(db.getConversationMessages("main", 100).none { it.id == id })
    }

    @Test
    fun `update message content by id updates memory and store`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        manager.appendSystemMessage("old")
        val id = manager.getHistory().single().id

        manager.updateMessageContentById(id!!, "new")

        assertEquals("new", manager.getHistory().single().content)
        assertEquals("new", db.getConversationMessages("main", 100).single().content)
    }

    @Test
    fun `tool call event is recorded as concise history entry`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        val now = Instant.now()
        val event =
            ToolCallEvent(
                toolId = "file:read",
                functionName = "read_file",
                inputJson = "{}",
                context = mapOf("requestId" to "r1"),
                result = ToolResult(toolId = "file:read", success = true, content = "first line of file content"),
                startedAtUtc = now,
                finishedAtUtc = now,
                durationMillis = 12,
            )

        manager.recordToolCall(event)

        val history = manager.getHistory()
        assertEquals(1, history.size)
        assertEquals("tool", history.single().role)
        assertTrue(history.single().content.contains("file:read"))
        assertTrue(
            history
                .single()
                .metadata
                .orEmpty()
                .contains("tool_call"),
        )
    }

    @Test
    fun `send message to existing agent returns assistant content`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse(
                    model = "test",
                    message = Message("assistant", "Done."),
                    done = true,
                )
            val agent = manager.createAgent("Worker", "Worker role")

            val answer = manager.sendMessageToAgent(agent.id, "Do work")

            assertEquals("Done.", answer)
            assertEquals(2, agent.chatHistory.size)
        }

    @Test
    fun `send message to unknown agent returns error string`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())

            val answer = manager.sendMessageToAgent("missing", "Do work")

            assertEquals("Error: Agent not found", answer)
        }

    @Test
    fun `append system message persists in history`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())

        manager.appendSystemMessage("You are helpful.")

        assertEquals("system", manager.getHistory().single().role)
        assertEquals("You are helpful.", manager.getHistory().single().content)
    }
}
