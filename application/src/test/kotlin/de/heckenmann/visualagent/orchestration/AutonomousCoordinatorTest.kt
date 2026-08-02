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

class AutonomousCoordinatorTest {
    @Test
    fun `auto pickup assigns pending todo to idle agent and schedules work`(): Unit =
        runBlocking {
            val fixture = buildFixture(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.todoManager.add("Implement feature", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(2000)

                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
                assertTrue(fixture.notifications.any { it.contains("STATUS:BUSY") })
                assertTrue(fixture.messages.any { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `auto pickup auto assigns unassigned todo to idle agent`(): Unit =
        runBlocking {
            val fixture = buildFixture(chatDelayMs = 5000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Implement feature")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(2000)

                assertEquals("agent-1", todo.assignedAgentId)
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
                assertTrue(fixture.messages.any { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `auto pickup does nothing when all agents are busy`() =
        runBlocking {
            val fixture = buildFixture()
            fixture.todoManager.add("Implement feature", "agent-1")
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.BUSY))

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(800)

                assertTrue(fixture.messages.none { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `auto pickup skips todo assigned to missing agent`() =
        runBlocking {
            val fixture = buildFixture()
            fixture.todoManager.add("Implement feature", "agent-missing")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(800)

                assertTrue(fixture.messages.none { it.content.contains("Started todo") })
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `auto pickup respects parallelism limit`() =
        runBlocking {
            val fixture = buildFixture(parallelism = 1, chatDelayMs = 5000)
            fixture.todoManager.add("Task 1", "agent-1")
            fixture.todoManager.add("Task 2", "agent-2")
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            fixture.putSubAgent(SubAgent(id = "agent-2", name = "Tester", role = "Testing", status = AgentStatus.IDLE))

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                delay(1200)

                assertEquals(1, fixture.subAgents.values.count { it.status == AgentStatus.BUSY })
            } finally {
                fixture.cancel()
            }
        }

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

                assertEquals(
                    TodoStatus.COMPLETED,
                    fixture.todoManager.getById(todo.id)!!.status,
                )
                val completion = fixture.messages.firstOrNull { it.content.contains("completed todo") }
                assertNotNull(completion)
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
                assertTrue(fixture.subAgents["agent-1"]?.status == AgentStatus.IDLE)

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
