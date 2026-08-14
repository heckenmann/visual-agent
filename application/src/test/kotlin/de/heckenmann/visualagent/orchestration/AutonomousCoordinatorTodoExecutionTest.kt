package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutonomousCoordinatorTodoExecutionTest {
    @Test
    fun `individual execution controls leave completed todo unchanged`() =
        runBlocking {
            val fixture = buildFixture()
            val todo = fixture.todoManager.add("Completed task")
            fixture.todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)

            try {
                assertFalse(fixture.coordinator.startTodo(todo.id))
                assertFalse(fixture.coordinator.stopTodo(todo.id))
                val persisted = fixture.todoManager.getById(todo.id)
                assertEquals(TodoStatus.COMPLETED, persisted?.status)
                assertEquals(todo.completedAt, persisted?.completedAt)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `start all ignores completed todos`() =
        runBlocking {
            val fixture = buildFixture(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val completed = fixture.todoManager.add("Already completed")
            fixture.todoManager.updateStatus(completed.id, TodoStatus.COMPLETED)
            val pending = fixture.todoManager.add("Still pending", "agent-1")

            try {
                assertEquals(1, fixture.coordinator.startAllTodos())
                delay(1500)

                assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(completed.id)?.status)
                assertEquals(TodoStatus.IN_PROGRESS, fixture.todoManager.getById(pending.id)?.status)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `starting one todo does not pick an earlier pending todo`() =
        runBlocking {
            val fixture = buildFixture(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.putSubAgent(SubAgent(id = "agent-2", name = "Tester", role = "Testing", status = AgentStatus.IDLE))
            val first = fixture.todoManager.add("First task", "agent-1")
            val second = fixture.todoManager.add("Second task", "agent-2")

            try {
                assertTrue(fixture.coordinator.startTodo(second.id))
                delay(1500)

                assertEquals(TodoStatus.PENDING, fixture.todoManager.getById(first.id)?.status)
                assertEquals(TodoStatus.IN_PROGRESS, fixture.todoManager.getById(second.id)?.status)
                assertEquals(AgentStatus.IDLE, fixture.subAgents["agent-1"]?.status)
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-2"]?.status)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `global pause prevents decomposition from invoking an analyst`() =
        runBlocking {
            val fixture = buildFixture()
            val todo = fixture.todoManager.add("Implement a broad feature with tests, documentation, and rollout validation")
            fixture.executionControl.pauseAll()

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(800)

                assertEquals(TodoStatus.PENDING, fixture.todoManager.getById(todo.id)?.status)
                assertFalse(fixture.subAgents.values.any { it.name.contains("analyst", ignoreCase = true) })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `paused worker does not hold the scheduler slot at the next execution boundary`() =
        runBlocking {
            val fixture = buildFixture(parallelism = 1, chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.putSubAgent(SubAgent(id = "agent-2", name = "Tester", role = "Testing", status = AgentStatus.IDLE))
            fixture.todoManager.add("First task", "agent-1")
            fixture.todoManager.add("Second task", "agent-2")

            try {
                fixture.coordinator.startAllTodos()
                delay(500)
                fixture.executionControl.pauseAgent("agent-1")

                withTimeout(8_000) {
                    while (fixture.subAgents["agent-2"]?.status != AgentStatus.BUSY) delay(100)
                }
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `stop all ignores completed todos`() =
        runBlocking {
            val fixture = buildFixture()
            val completed = fixture.todoManager.add("Already completed")
            fixture.todoManager.updateStatus(completed.id, TodoStatus.COMPLETED)
            val pending = fixture.todoManager.add("Pending task")

            try {
                assertEquals(1, fixture.coordinator.stopAllTodos())

                assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(completed.id)?.status)
                assertEquals(TodoStatus.CANCELLED, fixture.todoManager.getById(pending.id)?.status)
            } finally {
                fixture.cancel()
            }
        }
}
