package de.heckenmann.visualagent.ui.conversation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.TodoChange
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoProgress
import de.heckenmann.visualagent.protocol.TodoResponseSnapshot
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.ui.todo.TodoResponseState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Reproduces initial snapshot races through the real event collectors, without sleeps. */
class ConversationTodoInitialLoadTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `late initial response read must not revert a newer lifecycle status`() {
        val fixture = show()
        try {
            compose.runOnIdle {
                fixture.listener.captured(TodoChange(todo = fixture.todo.copy(status = TodoState.IN_PROGRESS), todoId = fixture.todo.id))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(
                    TodoState.IN_PROGRESS,
                    fixture.state
                        .get()
                        .todos
                        .single()
                        .status,
                )
            }
            finishLoad(fixture)
            compose.runOnIdle {
                assertEquals(
                    setOf(fixture.todo.id, "sentinel"),
                    fixture.state
                        .get()
                        .todos
                        .map { it.id }
                        .toSet(),
                )
                assertEquals(
                    TodoState.IN_PROGRESS,
                    fixture.state
                        .get()
                        .todos
                        .first { it.id == fixture.todo.id }
                        .status,
                )
            }
        } finally {
            fixture.release.countDown()
        }
    }

    @Test
    fun `empty holder opened during load receives the persisted response in place`() {
        val fixture = show()
        try {
            compose.runOnIdle {
                fixture.listener.captured(TodoChange(todo = fixture.todo.copy(description = "Updated task"), todoId = fixture.todo.id))
            }
            compose.waitForIdle()
            val opened = AtomicReference<TodoResponseState>()
            compose.runOnIdle { opened.set(fixture.state.get().responseFor(fixture.todo.id)) }
            finishLoad(fixture)
            compose.runOnIdle {
                assertSame(opened.get(), fixture.state.get().responseFor(fixture.todo.id))
                assertEquals("Saved response", opened.get().text)
                assertEquals(
                    "Updated task",
                    fixture.state
                        .get()
                        .todos
                        .first { it.id == fixture.todo.id }
                        .description,
                )
            }
        } finally {
            fixture.release.countDown()
        }
    }

    @Test
    fun `deleted todo is not resurrected by an older initial snapshot`() {
        val fixture = show()
        try {
            compose.runOnIdle {
                fixture.listener.captured(TodoChange(todo = fixture.todo, todoId = fixture.todo.id, removed = true))
            }
            compose.waitForIdle()
            finishLoad(fixture)
            compose.runOnIdle {
                assertTrue(
                    fixture.state
                        .get()
                        .todos
                        .none { it.id == fixture.todo.id },
                )
                assertEquals(fixture.todo, fixture.state.get().deletedSnapshots[fixture.todo.id])
            }
        } finally {
            fixture.release.countDown()
        }
    }

    @Test
    fun `new live execution is not overwritten by persisted output from the prior execution`() {
        val fixture = show()
        try {
            compose.runOnIdle {
                fixture.progress.captured(TodoProgress(fixture.todo.id, "Current output", executionId = "current", agentId = "worker"))
            }
            compose.waitForIdle()
            finishLoad(fixture)
            compose.runOnIdle {
                val response = fixture.state.get().responseFor(fixture.todo.id)
                assertEquals("Current output", response.text)
                assertEquals("current", response.executionId)
                assertTrue(response.isStreaming)
            }
        } finally {
            fixture.release.countDown()
        }
    }

    private fun finishLoad(fixture: Fixture) {
        fixture.release.countDown()
        compose.waitUntil {
            fixture.state
                .get()
                .responses
                .containsKey("sentinel")
        }
    }

    @Test
    fun `clear invalidates a pending initial snapshot`() {
        val state = ConversationTodoState()
        val token = state.initialLoadToken()
        val todo = TodoItem("old", "Cleared task")
        state.clear()
        state.restoreInitialSnapshots(token, listOf(todo), emptyList(), listOf(TodoResponseSnapshot(todo.id, "Old result")))
        assertTrue(state.todos.isEmpty())
        assertTrue(state.deletedSnapshots.isEmpty())
        assertTrue(state.responses.isEmpty())
    }

    @Test
    fun `id only deletion keeps the available initial snapshot as unavailable`() {
        val fixture = show()
        try {
            compose.runOnIdle {
                fixture.listener.captured(TodoChange(todoId = fixture.todo.id, removed = true))
            }
            compose.waitForIdle()
            finishLoad(fixture)
            compose.runOnIdle {
                assertTrue(
                    fixture.state
                        .get()
                        .todos
                        .none { it.id == fixture.todo.id },
                )
                assertEquals(fixture.todo, fixture.state.get().deletedSnapshots[fixture.todo.id])
            }
        } finally {
            fixture.release.countDown()
        }
    }

    private fun show(): Fixture {
        val fixture = Fixture()
        val todos = mockk<TodoPort>(relaxed = true)
        every { todos.list() } returns listOf(fixture.todo, TodoItem("sentinel", "Other task"))
        every { todos.deletedSnapshots(any()) } returns emptyList()
        every { todos.responseSnapshots(any()) } answers {
            fixture.entered.countDown()
            check(fixture.release.await(15, TimeUnit.SECONDS))
            listOf(TodoResponseSnapshot(fixture.todo.id, "Saved response"), TodoResponseSnapshot("sentinel", "Loaded"))
        }
        every { todos.addListener(capture(fixture.listener)) } returns AutoCloseable {}
        every { todos.addProgressListener(capture(fixture.progress)) } returns AutoCloseable {}
        val conversation = mockk<ConversationPort>(relaxed = true)
        coEvery { conversation.latest() } returns ConversationHistoryPage(emptyList(), 0, false)
        val history = ConversationUiState(emptyList())
        compose.setContent { fixture.state.set(rememberConversationTodoState(todos, conversation, history)) }
        try {
            compose.waitUntil { fixture.entered.count == 0L }
        } catch (error: Throwable) {
            fixture.release.countDown()
            throw error
        }
        return fixture
    }

    private class Fixture {
        val todo = TodoItem("todo", "Original task", TodoState.PENDING)
        val state = AtomicReference<ConversationTodoState>()
        val listener = slot<(TodoChange) -> Unit>()
        val progress = slot<(TodoProgress) -> Unit>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
    }
}
