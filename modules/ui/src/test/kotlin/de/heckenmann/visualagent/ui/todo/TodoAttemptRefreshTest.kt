package de.heckenmann.visualagent.ui.todo

import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoResponseSnapshot
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.task.TaskAttemptResult
import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.TaskOutcome
import de.heckenmann.visualagent.task.WorkerHandoff
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodoAttemptRefreshTest {
    private fun attempt(revision: Long) =
        TaskAttemptResult(
            "todo",
            "worker",
            "execution-$revision",
            1,
            revision,
            "worker/1",
            WorkerHandoff("Result $revision", TaskOutcome.PRODUCED),
            emptyList(),
            emptyList(),
            accepted = true,
            currentAccepted = true,
        )

    @Test
    fun `terminal reload updates the same overlay state and a new execution demotes acceptance`() =
        runBlocking {
            val todo = TodoItem("todo", "Task", TodoState.COMPLETED, contract = TaskContract(objective = "Task"))
            val port = mockk<TodoPort>()
            every { port.responseSnapshots(setOf("todo")) } returns
                listOf(TodoResponseSnapshot("todo", "Current result", attempts = listOf(attempt(1))))
            val response = TodoResponseState()
            val reload = TodoAttemptRefresh()
            reload.refresh(todo, port, response, { todo }, { false })
            assertEquals("Current result", response.text)
            assertTrue(response.attempts.single().currentAccepted)
            response.apply("next-execution", "worker", "Next", false)
            assertFalse(response.attempts.single().currentAccepted)
            assertTrue(response.isStreaming)
        }

    @Test
    fun `delayed old revision cannot overwrite the latest result`() =
        runBlocking {
            var current = TodoItem("todo", "Task", TodoState.COMPLETED, contract = TaskContract(objective = "Old"))
            val old = current
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val calls = AtomicInteger()
            val port = mockk<TodoPort>()
            every { port.responseSnapshots(setOf("todo")) } answers {
                if (calls.incrementAndGet() == 1) {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    listOf(TodoResponseSnapshot("todo", "Old", attempts = listOf(attempt(1))))
                } else {
                    listOf(TodoResponseSnapshot("todo", "New", attempts = listOf(attempt(2))))
                }
            }
            val response = TodoResponseState()
            val reload = TodoAttemptRefresh()
            val stale = async { reload.refresh(old, port, response, { current }, { false }) }
            yield()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            try {
                current = old.copy(contract = TaskContract(revision = 2, objective = "New"))
                reload.refresh(current, port, response, { current }, { false })
            } finally {
                release.countDown()
            }
            stale.await()
            assertEquals("New", response.text)
            assertEquals(2L, response.attempts.single().contractRevision)
            assertTrue(response.attempts.single().currentAccepted)
        }
}
