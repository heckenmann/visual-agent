package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutonomousCoordinatorLifecycleTest {
    @Test
    fun `seedUxTodos adds predefined seeds`() =
        runBlocking {
            val fixture = buildFixture()

            try {
                fixture.coordinator.seedUxTodos()

                assertEquals(19, fixture.todoManager.getAll().size)
                assertTrue(fixture.todoManager.getAll().any { it.description.contains("command palette") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `startAutonomousMode adds goal without seeding defaults`() =
        runBlocking {
            val fixture = buildFixture()

            try {
                fixture.coordinator.startAutonomousMode("Custom goal")

                assertEquals(1, fixture.todoManager.getAll().size)
                assertEquals(
                    "Custom goal",
                    fixture.todoManager
                        .getAll()
                        .single()
                        .description,
                )
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `start all todos resets cancelled work and schedules it`() =
        runBlocking {
            val fixture = buildFixture(workerResponseGate = CompletableDeferred())
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Retry task", "agent-1")
            fixture.todoManager.cancelTodo(todo.id)

            try {
                assertEquals(1, fixture.coordinator.startAllTodos())
                fixture.awaitWorkerStart()

                assertEquals(TodoStatus.IN_PROGRESS, fixture.todoManager.getById(todo.id)?.status)
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `stop todo cancels an in progress worker`() =
        runBlocking {
            val fixture = buildFixture(workerResponseGate = CompletableDeferred())
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Stop task", "agent-1")

            try {
                assertTrue(fixture.coordinator.startTodo(todo.id))
                fixture.awaitWorkerStart()
                assertEquals(TodoStatus.IN_PROGRESS, fixture.todoManager.getById(todo.id)?.status)

                assertTrue(fixture.coordinator.stopTodo(todo.id))
                assertEquals(TodoStatus.CANCELLED, fixture.todoManager.getById(todo.id)?.status)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `stop all todos cancels pending work`() =
        runBlocking {
            val fixture = buildFixture()
            fixture.todoManager.add("Task one")
            fixture.todoManager.add("Task two")

            try {
                assertEquals(2, fixture.coordinator.stopAllTodos())
                assertTrue(fixture.todoManager.getAll().all { it.status == TodoStatus.CANCELLED })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `completion message contains only todo id and get-result hint`() =
        runBlocking {
            val fixture = buildFixture()
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.todoManager.add("Implement feature", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                fixture.awaitWorkerCompletion()

                val completion = fixture.awaitMessageContaining("completed todo")
                assertTrue(completion.content.contains("Use `todos` with `get-result`"))
                assertTrue(completion.content.contains("completed todo"))
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `todo is completed even when sub-agent returns blank response`() =
        runBlocking {
            val fixture = buildFixture(responseContent = "")
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Task that yields no text", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                fixture.awaitWorkerCompletion()

                assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(todo.id)!!.status)
                assertNotNull(fixture.awaitMessageContaining("completed todo"))
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `transient worker failure is persisted before the retry succeeds`() =
        runBlocking {
            val fixture = buildFixture(failingWorkerAttempts = 1)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Retry after transient failure", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                fixture.awaitWorkerCompletion()

                assertTrue(fixture.awaitMessageContaining("failed attempt 1").content.contains("failed attempt 1"))
                assertTrue(fixture.awaitMessageContaining("completed todo ${todo.id}").content.contains("completed todo ${todo.id}"))
                assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(todo.id)?.status)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `sub-agent restarts when todo description is edited while running`() =
        runBlocking {
            val workerResponseGate = CompletableDeferred<Unit>()
            val fixture = buildFixture(workerResponseGate = workerResponseGate)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Old description", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                fixture.awaitWorkerStart()
                fixture.todoManager.update(todo.id, "New description")

                fixture.awaitMessageContaining("Todo ${todo.id} was updated")
                fixture.awaitWorkerStart()
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)

                workerResponseGate.complete(Unit)
                fixture.awaitWorkerCompletion()

                assertTrue(fixture.messages.any { it.content.contains("Todo ${todo.id} was updated") })
                assertTrue(fixture.awaitMessageContaining("completed todo ${todo.id}").content.contains("completed todo ${todo.id}"))
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `coordinator resumes loop when existing todo is reset to PENDING`() =
        runBlocking {
            val fixture = buildFixture(workerResponseGate = CompletableDeferred())
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Implement feature", "agent-1")
            fixture.todoManager.cancelTodo(todo.id)

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                assertEquals(AgentStatus.IDLE, fixture.subAgents["agent-1"]?.status)

                fixture.todoManager.updateStatus(todo.id, TodoStatus.PENDING)
                fixture.awaitWorkerStart()

                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
                assertTrue(fixture.messages.any { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `sub-agent stops when assigned agent changes while running`() =
        runBlocking {
            val fixture = buildFixture(workerResponseGate = CompletableDeferred())
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.putSubAgent(SubAgent(id = "agent-2", name = "Tester", role = "Testing", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Task", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                fixture.awaitWorkerStart()
                fixture.todoManager.updateAssignedAgent(todo.id, "agent-2")

                fixture.awaitMessageContaining("Stopped because the todo was cancelled, deleted, or reassigned")

                assertTrue(fixture.messages.any { it.content.contains("Stopped because the todo was cancelled, deleted, or reassigned") })
            } finally {
                fixture.cancel()
            }
        }
}
