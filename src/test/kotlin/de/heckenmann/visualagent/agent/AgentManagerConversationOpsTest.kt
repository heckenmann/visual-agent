package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
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
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
        manager.appendSystemMessage("first")
        val id = db.saveConversationMessage("main", "user", "second")
        val manager2 = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())

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
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
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
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
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
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
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
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())

            val answer = manager.sendMessageToAgent("missing", "Do work")

            assertEquals("Error: Agent not found", answer)
        }

    @Test
    fun `append system message persists in history`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())

        manager.appendSystemMessage("You are helpful.")

        assertEquals("system", manager.getHistory().single().role)
        assertEquals("You are helpful.", manager.getHistory().single().content)
    }

    @Test
    fun `stream message emits chunks and persists assistant response`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flowOf(
                    ChatResponse(model = "test", message = Message("assistant", "Hello"), done = false),
                    ChatResponse(model = "test", message = Message("assistant", " world"), done = true),
                )
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
            val chunks = mutableListOf<String>()

            val result = manager.streamMessage("hi") { chunks += it }

            assertEquals("Hello world", result)
            assertEquals(listOf("Hello", " world"), chunks)
            val history = manager.getHistory()
            assertEquals("user", history.first().role)
            assertEquals("assistant", history.last().role)
        }

    @Test
    fun `start agent job creates agent and executes task`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse(model = "test", message = Message("assistant", "result"), done = true)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())

            val jobResult = manager.startAgentJob("Worker", "Worker role", "coder", "write code")

            assertEquals("result", jobResult.content)
            assertEquals("Worker", jobResult.agentName)
            assertTrue(jobResult.agentId.isNotBlank())
        }

    @Test
    fun `run agent job executes task on existing agent`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse(model = "test", message = Message("assistant", "done"), done = true)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
            val agent = manager.createAgent("Worker", "Worker role")

            val jobResult = manager.runAgentJob(agent.id, "task")

            assertEquals("done", jobResult.content)
            assertEquals(agent.id, jobResult.agentId)
        }

    @Test
    fun `enqueue agent job returns job id and triggers completion callback`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse(model = "test", message = Message("assistant", "completed"), done = true)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
            val agent = manager.createAgent("Worker", "Worker role")
            val finished = kotlinx.coroutines.CompletableDeferred<String>()
            AgentManager.setAgentCallback { id, message ->
                if (id == agent.id && message.startsWith("Sub-agent job")) {
                    finished.complete(id)
                }
            }

            val jobId = manager.enqueueAgentJob(agent.id, "task")
            finished.await()

            assertTrue(jobId.isNotBlank())
            assertTrue(manager.getHistory().any { it.role == "sub_agent" })
        }
}
