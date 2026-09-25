package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.seedDefaultTestAgents
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerTodoTest {
    private suspend fun <R> useManager(block: suspend (TodoAgentManagerFixture) -> R): R {
        val fixture = createManager()
        return try {
            block(fixture)
        } finally {
            fixture.manager.destroy()
        }
    }

    private fun createManager(): TodoAgentManagerFixture {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:h2:mem:test")
        val provider = mockk<LLMProvider>(relaxed = true)
        seedDefaultTestAgents(db)
        coEvery { provider.isConnected() } returns true
        every { provider.chatReactive(any<ChatRequestContext>()) } answers {
            mono {
                awaitCancellation()
            }
        }
        return TodoAgentManagerFixture(
            manager =
                AgentManager(
                    db,
                    provider,
                    AgentToolConfigService(db),
                    ToolEventBus(),
                    TodoEventBus(),
                    AppConfigBean(db),
                ),
            provider = provider,
        )
    }

    @Test
    fun `autonomous pickup assigns pending todo to idle agent`(): Unit =
        runBlocking {
            useManager { fixture ->
                val manager = fixture.manager
                val todo = manager.todoManager.add("Research topic X", "1")

                manager.awaitTodoStatus(todo.id, TodoStatus.IN_PROGRESS) {
                    manager.startAutonomousProcessing(seed = false)
                }
                manager.awaitAgentStatus("1", AgentStatus.BUSY)

                val agent = manager.getSubAgents().first { it.id == "1" }
                assertEquals(AgentStatus.BUSY, agent.status)
                assertNotNull(agent.currentTodoId)
            }
        }

    @Test
    fun `autonomous pickup admits the topmost todo before later work`(): Unit =
        runBlocking {
            useManager { fixture ->
                val manager = fixture.manager
                manager.todoManager.add("Later task", "1")
                val top = manager.todoManager.add("Top task", "2")
                manager.todoManager.moveToPosition(top.id, 0)

                manager.awaitTodoStatus(top.id, TodoStatus.IN_PROGRESS) {
                    manager.startAutonomousProcessing(seed = false)
                }
                manager.awaitAgentStatus("2", AgentStatus.BUSY)

                val topAgent = manager.getSubAgent("2")
                assertEquals(AgentStatus.BUSY, topAgent?.status)
                assertEquals(top.id, topAgent?.currentTodoId)
            }
        }

    @Test
    fun `todo status transitions correctly on auto pickup`(): Unit =
        runBlocking {
            useManager { fixture ->
                val manager = fixture.manager
                val todo = manager.todoManager.add("Verify status", "1")

                manager.awaitTodoStatus(todo.id, TodoStatus.IN_PROGRESS) {
                    manager.startAutonomousProcessing(seed = false)
                }

                assertEquals(TodoStatus.IN_PROGRESS, manager.todoManager.getById(todo.id)!!.status)
                assertNotNull(manager.todoManager.getById(todo.id)!!.assignedAgentId)
            }
        }

    @Test
    fun `only one agent gets busy per pickup slot`(): Unit =
        runBlocking {
            useManager { fixture ->
                val manager = fixture.manager
                val todo = manager.todoManager.add("Solo task", "1")

                manager.awaitTodoStatus(todo.id, TodoStatus.IN_PROGRESS) {
                    manager.startAutonomousProcessing(seed = false)
                }
                manager.awaitAgentStatus("1", AgentStatus.BUSY)

                val busyCount = manager.getSubAgents().count { it.status == AgentStatus.BUSY }
                assertEquals(1, busyCount)
            }
        }

    @Test
    fun `agent currentTodoId is null when idle`() {
        val manager = createManager().manager

        manager.getSubAgents().forEach { agent ->
            assertNull(agent.currentTodoId)
            assertEquals(AgentStatus.IDLE, agent.status)
            assertNull(agent.currentTask)
        }
    }

    @Test
    fun `todo mutation creates conversation messages`(): Unit =
        runBlocking {
            val fixture = createManager()
            val manager = fixture.manager
            manager.todoManager.add("A new task", "1")

            val history = manager.getHistory()
            assertTrue(history.any { it.role == "system" && it.content.contains("A new task") })
        }

    private suspend fun AgentManager.awaitTodoStatus(
        todoId: String,
        status: TodoStatus,
        trigger: () -> Unit,
    ) {
        val observed = CompletableDeferred<TodoChange>()
        val listener =
            todoEventBus.addListener { change ->
                if (change.todo?.id == todoId && change.todo.status == status) observed.complete(change)
            }
        try {
            trigger()
            observed.await()
        } finally {
            listener.close()
        }
    }

    private suspend fun AgentManager.awaitAgentStatus(
        agentId: String,
        status: AgentStatus,
    ) {
        val observed = CompletableDeferred<Unit>()
        val listener =
            agentStatusCallbackAdapter.addListener { changedAgentId, message ->
                if (changedAgentId == agentId && message == "STATUS:${status.name}") observed.complete(Unit)
            }
        try {
            if (getSubAgent(agentId)?.status != status) observed.await()
        } finally {
            listener.close()
        }
    }
}
