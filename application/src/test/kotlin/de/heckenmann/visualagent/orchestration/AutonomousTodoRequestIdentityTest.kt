package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutonomousTodoRequestIdentityTest {
    @Test
    fun `invalidated start does not terminate the pickup loop`() =
        runBlocking {
            val rejected = AtomicBoolean(false)
            val fixture =
                buildFixture(onPersistMessage = {
                    if (it.content.startsWith("Started todo") && rejected.compareAndSet(false, true)) {
                        throw CancellationException("Conversation request was invalidated by reset")
                    }
                })
            fixture.putSubAgent(SubAgent(id = "worker", name = "Worker", role = "Implementation"))
            try {
                kotlinx.coroutines.withTimeout(10000) {
                    val todo = fixture.todoManager.add("Resume pickup after reset", "worker")
                    fixture.coordinator.startTodo(todo.id)
                    fixture.awaitWorkerStart()
                    fixture.awaitMessageContaining("completed todo ${todo.id}")
                    assertTrue(rejected.get(), "The first start must exercise request invalidation")
                    assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(todo.id)?.status)
                }
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `todo start and completion share a registered request identity`() =
        runBlocking {
            val fixture = buildFixture()
            fixture.putSubAgent(SubAgent(id = "worker", name = "Worker", role = "Implementation"))
            try {
                val todo = fixture.todoManager.add("A correlated task", "worker")
                fixture.coordinator.startTodo(todo.id)
                fixture.awaitTodoStatus(todo.id, TodoStatus.COMPLETED)
                val started = fixture.messages.single { it.content.startsWith("Started todo ${todo.id}") }
                val completed = fixture.awaitMessageContaining("completed todo ${todo.id}")
                assertNotNull(started.conversationRequestId)
                assertEquals(started.conversationRequestId, completed.conversationRequestId)
            } finally {
                fixture.cancel()
            }
        }
}
