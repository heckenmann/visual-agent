package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoUpdateCommand
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises edits at the claim and guarded completion boundaries without timing sleeps. */
class AutonomousTodoClaimRaceTest {
    @Test
    fun `edit after claim before processor start executes only the new objective`() =
        runBlocking {
            withTimeout(10000) {
                lateinit var fixture: CoordinatorFixture
                val edited = AtomicBoolean()
                fixture =
                    buildFixture(onPersistMessage = { message ->
                        if (message.content.startsWith("Started todo") && edited.compareAndSet(false, true)) {
                            val todo = fixture.todoManager.getAll().single()
                            assertTrue(fixture.todoManager.update(TodoUpdateCommand(todo.id, description = "New objective")))
                        }
                    })
                fixture.putSubAgent(SubAgent("worker", "Worker", "Implementation"))
                val todo = fixture.todoManager.add("Old objective", "worker")
                try {
                    assertTrue(fixture.coordinator.startTodo(todo.id))
                    fixture.awaitMessageContaining("completed todo ${todo.id}")
                    val workers = fixture.providerRequests.filter { it.metadata["sessionId"] != "review" }
                    assertEquals(1, workers.size)
                    assertTrue(workers.single().messages.any { it.content.contains("New objective") })
                    assertTrue(workers.single().messages.none { it.content.contains("Old objective") })
                    assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(todo.id)?.status)
                } finally {
                    fixture.cancel()
                }
            }
        }

    @Test
    fun `edit inside completion CAS requeues individual work instead of orphaning it`() =
        runBlocking {
            withTimeout(10000) {
                val backing = FakeTodoStore()
                val edited = AtomicBoolean()
                lateinit var fixture: CoordinatorFixture
                val store =
                    object : TodoStore by backing {
                        override fun updateTodoIfCurrent(
                            expected: Todo,
                            updated: Todo,
                        ): Boolean {
                            if (updated.status == TodoStatus.COMPLETED && edited.compareAndSet(false, true)) {
                                assertTrue(fixture.todoManager.update(TodoUpdateCommand(expected.id, description = "New objective")))
                            }
                            return backing.updateTodoIfCurrent(expected, updated)
                        }
                    }
                fixture = buildFixture(todoStore = store)
                fixture.putSubAgent(SubAgent("worker", "Worker", "Implementation"))
                val todo = fixture.todoManager.add("Old objective", "worker")
                try {
                    assertTrue(fixture.coordinator.startTodo(todo.id))
                    fixture.awaitMessageContaining("completed todo ${todo.id}")
                    val workers = fixture.providerRequests.filter { it.metadata["sessionId"] != "review" }
                    assertEquals(2, workers.size)
                    assertTrue(workers.last().messages.any { it.content.contains("New objective") })
                    assertEquals("New objective", fixture.todoManager.getById(todo.id)?.description)
                    assertEquals(TodoStatus.COMPLETED, fixture.todoManager.getById(todo.id)?.status)
                    assertEquals(1, fixture.messages.count { it.content.contains("completed todo ${todo.id}") })
                } finally {
                    fixture.cancel()
                }
            }
        }
}
