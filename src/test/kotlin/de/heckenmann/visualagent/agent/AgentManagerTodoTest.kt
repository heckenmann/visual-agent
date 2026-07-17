package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.PersistenceStores
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentManagerTodoTest {
    private suspend fun <R> useManager(block: suspend (AgentManager) -> R): R {
        val (manager, _, _) = createManager()
        return try {
            block(manager)
        } finally {
            manager.destroy()
        }
    }

    private fun createManager(): Triple<AgentManager, LLMProvider, PersistenceStores> {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        coEvery { provider.isConnected() } returns true
        coEvery { provider.chat(any<ChatRequestContext>()) } coAnswers {
            delay(3000)
            ChatResponse(
                model = "test",
                message = Message("assistant", "Task completed"),
                done = true,
            )
        }
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
        return Triple(manager, provider, db)
    }

    private fun createManagerWithInstantResponse(): Triple<AgentManager, LLMProvider, PersistenceStores> {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        coEvery { provider.isConnected() } returns true
        coEvery { provider.chat(any<ChatRequestContext>()) } returns
            ChatResponse(
                model = "test",
                message = Message("assistant", "Task completed"),
                done = true,
            )
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
        return Triple(manager, provider, db)
    }

    @Test
    fun `autonomous pickup assigns pending todo to idle agent`(): Unit =
        runBlocking {
            useManager { manager ->
                manager.todoManager.add("Research topic X", "1")

                manager.startAutonomousProcessing(seed = false)
                delay(1200)

                val agent = manager.getSubAgents().first { it.id == "1" }
                assertEquals(AgentStatus.BUSY, agent.status)
                assertNotNull(agent.currentTodoId)
            }
        }

    @Test
    fun `autonomous pickup does nothing when no pending todos`(): Unit =
        runBlocking {
            useManager { manager ->
                manager.startAutonomousProcessing(seed = false)
                delay(800)

                assertTrue(manager.getSubAgents().all { it.status == AgentStatus.IDLE })
            }
        }

    @Test
    fun `autonomous pickup does nothing when no idle agents`(): Unit =
        runBlocking {
            useManager { manager ->
                manager.getSubAgents().forEach { it.status = AgentStatus.BUSY }
                manager.todoManager.add("Orphan task", "1")

                manager.startAutonomousProcessing(seed = false)
                delay(800)

                assertEquals(
                    TodoStatus.PENDING,
                    manager.todoManager
                        .getAll()
                        .single()
                        .status,
                )
            }
        }

    @Test
    fun `autonomous pickup picks first idle agent and topmost pending todo by position`(): Unit =
        runBlocking {
            useManager { manager ->
                manager.todoManager.add("Later task", "1")
                val top = manager.todoManager.add("Top task", "2")
                manager.todoManager.moveToPosition(top.id, 0)

                manager.startAutonomousProcessing(seed = false)
                delay(1200)

                val busyAgent = manager.getSubAgents().first { it.status == AgentStatus.BUSY }
                assertEquals(top.id, busyAgent.currentTodoId)
            }
        }

    @Test
    fun `todo status transitions correctly on auto pickup`(): Unit =
        runBlocking {
            useManager { manager ->
                val todo = manager.todoManager.add("Verify status", "1")

                manager.startAutonomousProcessing(seed = false)
                delay(1200)

                assertEquals(TodoStatus.IN_PROGRESS, manager.todoManager.getById(todo.id)!!.status)
                assertNotNull(manager.todoManager.getById(todo.id)!!.assignedAgentId)
            }
        }

    @Test
    fun `only one agent gets busy per pickup slot`(): Unit =
        runBlocking {
            useManager { manager ->
                manager.todoManager.add("Solo task", "1")

                manager.startAutonomousProcessing(seed = false)
                delay(1200)

                val busyCount = manager.getSubAgents().count { it.status == AgentStatus.BUSY }
                assertEquals(1, busyCount)
            }
        }

    @Test
    fun `agent currentTodoId is null when idle`() {
        val (manager, _, _) = createManager()

        manager.getSubAgents().forEach { agent ->
            assertNull(agent.currentTodoId)
            assertEquals(AgentStatus.IDLE, agent.status)
            assertNull(agent.currentTask)
        }
    }

    @Test
    fun `agents have expected default roles`() {
        val (manager, _, _) = createManager()
        val agents = manager.getSubAgents()

        assertEquals(3, agents.size)
        val agentsByName = agents.associateBy { it.name }
        assertTrue(agentsByName.containsKey("Researcher"))
        assertTrue(agentsByName.containsKey("Coder"))
        assertTrue(agentsByName.containsKey("Documenter"))
    }

    @Test
    fun `todo mutation creates conversation messages`(): Unit =
        runBlocking {
            val (manager, _, _) = createManager()
            manager.todoManager.add("A new task", "1")
            delay(200)

            val history = manager.getHistory()
            assertTrue(history.any { it.role == "system" && it.content.contains("A new task") })
        }

    @Test
    fun `main agent is triggered when todo is completed`(): Unit =
        runBlocking {
            val (manager, provider, _) = createManagerWithInstantResponse()
            val todo = manager.todoManager.add("Trigger test", "1")
            delay(200)

            manager.todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)
            delay(500)

            coVerify(atLeast = 1) { provider.chat(any<ChatRequestContext>()) }
            val history = manager.getHistory()
            assertTrue(
                history.any { it.role == "assistant" && it.content.isNotBlank() },
                "Expected a persisted assistant response after todo completion, got: ${history.map { it.role to it.content.take(60) }}",
            )
        }

    @Test
    fun `main agent is triggered when todo is cancelled`(): Unit =
        runBlocking {
            val (manager, provider, _) = createManagerWithInstantResponse()
            val todo = manager.todoManager.add("Cancel trigger test", "1")
            delay(200)

            manager.todoManager.updateStatus(todo.id, TodoStatus.CANCELLED)
            delay(500)

            coVerify(atLeast = 1) { provider.chat(any<ChatRequestContext>()) }
            val history = manager.getHistory()
            assertTrue(
                history.any { it.role == "assistant" && it.content.isNotBlank() },
                "Expected a persisted assistant response after todo cancellation, got: ${history.map { it.role to it.content.take(60) }}",
            )
        }

    @Test
    fun `main agent is not triggered for non-terminal status changes`(): Unit =
        runBlocking {
            val (manager, provider, _) = createManagerWithInstantResponse()
            val todo = manager.todoManager.add("No trigger", "1")
            delay(200)

            manager.todoManager.updateStatus(todo.id, TodoStatus.IN_PROGRESS)
            delay(200)

            coVerify(exactly = 0) { provider.chat(any<ChatRequestContext>()) }
        }
}
