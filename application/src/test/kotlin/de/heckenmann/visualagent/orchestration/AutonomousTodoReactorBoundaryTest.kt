package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import reactor.core.scheduler.Schedulers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ensures native provider/database signals cannot run blocking lifecycle callbacks on event loops. */
class AutonomousTodoReactorBoundaryTest {
    @Test
    fun `provider failure on a nonblocking thread retries and persists completion through lifecycle adapters`() =
        runBlocking {
            val backing = FakeTodoStore()
            val store =
                object : TodoStore by backing {
                    override fun listTodosReactive() = backing.listTodosReactive().publishOn(Schedulers.parallel())
                }
            val fixture =
                buildFixture(
                    failingWorkerAttempts = 1,
                    todoStore = store,
                    providerSignalsOn = Schedulers.parallel(),
                    onPersistMessage = {
                        assertFalse(Schedulers.isInNonBlockingThread(), "Synchronous lifecycle persistence must use bounded elastic")
                    },
                )
            fixture.putSubAgent(SubAgent("worker", "Worker", "Implementation"))
            try {
                withTimeout(10000) {
                    val todo = fixture.todoManager.add("Complete after a transient provider failure", "worker")
                    assertTrue(fixture.coordinator.startTodo(todo.id))
                    fixture.awaitMessageContaining("completed todo ${todo.id}")
                    assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(todo.id)?.status)
                    assertEquals(2, fixture.providerRequests.count { it.metadata["sessionId"] != "review" })
                    assertTrue(fixture.messages.any { it.content.contains("failed attempt 1") })
                }
            } finally {
                fixture.cancel()
            }
        }
}
