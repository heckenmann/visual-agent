package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
