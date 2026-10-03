package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutonomousTodoRequestIdentityTest {
    @Test
    fun `invalidated start does not terminate the pickup loop`() =
        runBlocking {
            val parent = SupervisorJob()
            val rejected = AtomicBoolean(false)
            val fixture =
                buildFixture(
                    fixtureScope = CoroutineScope(parent + Dispatchers.Default),
                    onPersistMessage = {
                        if (it.content.startsWith("Started todo") && rejected.compareAndSet(false, true)) {
                            throw CancellationException("Conversation request was invalidated by reset")
                        }
                    },
                )
            val pickupStopped = CompletableDeferred<Unit>()
            val pickup = parent.children.single()
            val registration = pickup.invokeOnCompletion { pickupStopped.complete(Unit) }
            val workerStarted =
                async {
                    fixture.awaitWorkerStart()
                    true
                }
            fixture.putSubAgent(SubAgent(id = "worker", name = "Worker", role = "Implementation"))
            try {
                val todo = fixture.todoManager.add("Resume pickup after reset", "worker")
                fixture.coordinator.startTodo(todo.id)
                val continued =
                    select<Boolean> {
                        workerStarted.onAwait { it }
                        pickupStopped.onAwait { false }
                    }
                assertTrue(continued, "An invalidated request must not cancel the coordinator")
                fixture.awaitTodoStatus(todo.id, TodoStatus.COMPLETED)
            } finally {
                registration.dispose()
                workerStarted.cancel()
                workerStarted.join()
                fixture.cancel()
                parent.join()
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
                val completed = fixture.messages.single { it.content.contains("completed todo ${todo.id}") }
                assertNotNull(started.conversationRequestId)
                assertEquals(started.conversationRequestId, completed.conversationRequestId)
            } finally {
                fixture.cancel()
            }
        }
}
