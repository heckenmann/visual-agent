package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.delay
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
            val fixture = buildFixture(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Retry task", "agent-1")
            fixture.todoManager.cancelTodo(todo.id)

            try {
                assertEquals(1, fixture.coordinator.startAllTodos())
                delay(1500)

                assertEquals(TodoStatus.IN_PROGRESS, fixture.todoManager.getById(todo.id)?.status)
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `stop todo cancels an in progress worker`() =
        runBlocking {
            val fixture = buildFixture(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Stop task", "agent-1")

            try {
                assertTrue(fixture.coordinator.startTodo(todo.id))
                delay(1500)
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
                delay(1000)

                val completion = fixture.messages.firstOrNull { it.content.contains("completed todo") }
                requireNotNull(completion)
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
                delay(1000)

                assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(todo.id)!!.status)
                assertNotNull(fixture.messages.firstOrNull { it.content.contains("completed todo") })
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
                delay(2_000)

                assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(todo.id)?.status)
                assertTrue(fixture.messages.any { it.content.contains("failed attempt 1") })
                assertTrue(fixture.messages.any { it.content.contains("completed todo ${todo.id}") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `sub-agent restarts when todo description is edited while running`() =
        runBlocking {
            val fixture = buildFixture(chatDelayMs = 1000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Old description", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(400)
                fixture.todoManager.update(todo.id, "New description")

                delay(4000)

                assertTrue(fixture.messages.any { it.content.contains("Todo ${todo.id} was updated") })
                assertTrue(fixture.messages.any { it.content.contains("completed todo ${todo.id}") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `coordinator resumes loop when existing todo is reset to PENDING`() =
        runBlocking {
            val fixture = buildFixture(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Implement feature", "agent-1")
            fixture.todoManager.cancelTodo(todo.id)

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(400)
                assertEquals(AgentStatus.IDLE, fixture.subAgents["agent-1"]?.status)

                fixture.todoManager.updateStatus(todo.id, TodoStatus.PENDING)
                delay(2000)

                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
                assertTrue(fixture.messages.any { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `sub-agent stops when assigned agent changes while running`() =
        runBlocking {
            val fixture = buildFixture(chatDelayMs = 1000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.putSubAgent(SubAgent(id = "agent-2", name = "Tester", role = "Testing", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Task", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(400)
                fixture.todoManager.updateAssignedAgent(todo.id, "agent-2")

                delay(2500)

                assertTrue(fixture.messages.any { it.content.contains("Stopped because the todo was cancelled, deleted, or reassigned") })
            } finally {
                fixture.cancel()
            }
        }
}
