package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutonomousCoordinatorTest {
    @Test
    fun `worker begins without an artificial post-claim delay`() =
        runBlocking {
            val workerStreamStarted = CompletableDeferred<Unit>()
            val fixture = buildFixture(onWorkerStreamStarted = { workerStreamStarted.complete(Unit) })
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val todo = fixture.todoManager.add("Implement feature", "agent-1")

            try {
                assertTrue(fixture.coordinator.startTodo(todo.id))

                withTimeout(50) { workerStreamStarted.await() }
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `complex decomposition does not delay a simple todo pickup`() =
        runBlocking {
            val simpleWorkerStarted = CompletableDeferred<Unit>()
            val fixture =
                buildFixture(
                    chatDelayMs = 5_000,
                    onWorkerStreamStarted = { simpleWorkerStarted.complete(Unit) },
                )
            fixture.putSubAgent(
                SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE),
            )
            fixture.todoManager.add(
                "Analyze and integrate a multi-service architecture pipeline, then plan migration, tests, and documentation",
            )
            fixture.todoManager.add("Write the release notes", "agent-1")

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)

                withTimeout(50) { simpleWorkerStarted.await() }
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `pending todo wakes an already waiting coordinator without polling`() =
        runBlocking {
            val fixture = buildFixture(chatDelayMs = 5_000)
            fixture.putSubAgent(SubAgent(id = "agent-1", name = "Coder", role = "Implementation", status = AgentStatus.IDLE))
            val claimed = CompletableDeferred<Unit>()
            val registration =
                fixture.todoManager.addListener { change ->
                    if (change.todo?.status == TodoStatus.IN_PROGRESS) claimed.complete(Unit)
                }

            try {
                fixture.coordinator.startAutonomousProcessing(seed = false)
                fixture.todoManager.add("Implement feature", "agent-1")

                withTimeout(500) { claimed.await() }
                assertEquals(AgentStatus.BUSY, fixture.subAgents["agent-1"]?.status)
            } finally {
                registration.close()
                fixture.cancel()
            }
        }

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
}
