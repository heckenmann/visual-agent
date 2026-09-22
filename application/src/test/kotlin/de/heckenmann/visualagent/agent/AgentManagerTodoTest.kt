package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.seedDefaultTestAgents
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoTerminalReason
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
    private suspend fun <R> useManager(block: suspend (ManagerFixture) -> R): R {
        val fixture = createManager()
        return try {
            block(fixture)
        } finally {
            fixture.manager.destroy()
        }
    }

    private fun createManager(): ManagerFixture {
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
        return ManagerFixture(
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

    private fun createManagerWithInstantResponse(): ManagerFixture {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:h2:mem:test")
        val provider = mockk<LLMProvider>(relaxed = true)
        seedDefaultTestAgents(db)
        coEvery { provider.isConnected() } returns true
        every { provider.chatReactive(any<ChatRequestContext>()) } returns
            mono {
                ChatResponse(
                    model = "test",
                    message = Message("assistant", "Task completed"),
                    done = true,
                )
            }
        return ManagerFixture(
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

    @Test
    fun `todo completion persists system message`(): Unit =
        runBlocking {
            val fixture = createManagerWithInstantResponse()
            val manager = fixture.manager
            val todo = manager.todoManager.add("Trigger test", "1")

            manager.todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)

            val history = manager.getHistory()
            assertTrue(
                history.any { it.role == "system" && it.content.contains("[COMPLETED]") },
                "Expected a system message after todo completion, got: ${history.map { it.role to it.content.take(60) }}",
            )
        }

    @Test
    fun `todo cancellation persists system message`(): Unit =
        runBlocking {
            val fixture = createManagerWithInstantResponse()
            val manager = fixture.manager
            val todo = manager.todoManager.add("Cancel trigger test", "1")

            manager.todoManager.updateStatus(todo.id, TodoStatus.CANCELLED)

            val history = manager.getHistory()
            assertTrue(
                history.any { it.role == "system" && it.content.contains("[CANCELLED]") },
                "Expected a system message after todo cancellation, got: ${history.map { it.role to it.content.take(60) }}",
            )
        }

    @Test
    fun `completed todo review request ends with an explicit user instruction`(): Unit =
        runBlocking {
            val fixture = createManagerWithInstantResponse()
            val manager = fixture.manager
            val provider = fixture.provider
            val request = CompletableDeferred<ChatRequestContext>()
            every { provider.chatReactive(any<ChatRequestContext>()) } answers {
                mono {
                    request.complete(firstArg<ChatRequestContext>())
                    ChatResponse(model = "test", message = Message("assistant", "Reviewed"), done = true)
                }
            }
            val todo = manager.todoManager.add("Completed review", "1")

            manager.todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)

            val completedRequest = request.await()
            val completedMessages = completedRequest.messages
            val completedMessage = completedMessages.last()
            assertEquals(
                "user",
                completedMessage.role,
            )
            assertEquals(
                "Review the todo with id=${todo.id} described in the preceding system notification " +
                    "and carry out its instructions. Do not substitute another todo.",
                completedMessage.content,
            )
            manager.destroy()
        }

    @Test
    fun `failed todo review request includes its terminal outcome`(): Unit =
        runBlocking {
            val fixture = createManagerWithInstantResponse()
            val manager = fixture.manager
            val provider = fixture.provider
            val request = CompletableDeferred<ChatRequestContext>()
            every { provider.chatReactive(any<ChatRequestContext>()) } answers {
                mono {
                    request.complete(firstArg<ChatRequestContext>())
                    ChatResponse(model = "test", message = Message("assistant", "Reviewed"), done = true)
                }
            }
            val todo = manager.todoManager.add("Cancelled review", "1")

            manager.todoManager.cancelTodo(todo.id, TodoTerminalReason.EXECUTION_FAILED)

            val cancelledRequest = request.await()
            val cancelledMessages = cancelledRequest.messages
            val cancelledMessage = cancelledMessages.last()
            assertEquals(
                "user",
                cancelledMessage.role,
            )
            assertEquals(
                "Review the todo with id=${todo.id} described in the preceding system notification " +
                    "and carry out its instructions. Do not substitute another todo.",
                cancelledMessage.content,
            )
            assertTrue(
                cancelledMessages.any { it.content.contains("EXECUTION_FAILED") },
                "Expected the main-agent review to receive the failed terminal outcome.",
            )
            manager.destroy()
        }

    @Test
    fun `terminal todo changes each create one main-agent review notification`() =
        runBlocking {
            val fixture = createManagerWithInstantResponse()
            val manager = fixture.manager
            val reviewRequests = CompletableDeferred<Int>()
            var requestCount = 0
            every { fixture.provider.chatReactive(any<ChatRequestContext>()) } answers {
                mono {
                    requestCount += 1
                    if (requestCount == 2) reviewRequests.complete(requestCount)
                    ChatResponse(model = "test", message = Message("assistant", "Reviewed"), done = true)
                }
            }
            val first = manager.todoManager.add("First terminal todo", "1")
            val second = manager.todoManager.add("Second terminal todo", "2")

            manager.todoManager.updateStatus(first.id, TodoStatus.COMPLETED)
            manager.todoManager.cancelTodo(second.id, TodoTerminalReason.EXECUTION_FAILED)

            reviewRequests.await()
            val terminalReviews =
                manager
                    .getHistory()
                    .filter { it.metadata?.contains("todo_terminal_transition") == true }
            assertTrue(terminalReviews.any { it.metadata?.contains(first.id) == true })
            assertTrue(terminalReviews.any { it.metadata?.contains(second.id) == true })
            manager.destroy()
        }

    @Test
    fun `non-terminal status change does not persist completion message`(): Unit =
        runBlocking {
            val fixture = createManagerWithInstantResponse()
            val manager = fixture.manager
            val todo = manager.todoManager.add("No trigger", "1")

            manager.todoManager.updateStatus(todo.id, TodoStatus.IN_PROGRESS)

            val history = manager.getHistory()
            assertTrue(
                history.none { it.role == "system" && it.content.contains("[COMPLETED]") },
                "Expected no completion system message for non-terminal status change",
            )
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

    private data class ManagerFixture(
        val manager: AgentManager,
        val provider: LLMProvider,
    )
}
