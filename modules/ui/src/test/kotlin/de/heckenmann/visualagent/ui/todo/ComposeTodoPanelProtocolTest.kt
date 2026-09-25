package de.heckenmann.visualagent.ui.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.LifecycleState
import de.heckenmann.visualagent.protocol.TodoChange
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoProgress
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies todo rendering and controls through the protocol boundary. */
class ComposeTodoPanelProtocolTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel renders item statuses and per-item controls`() {
        val todos =
            listOf(
                TodoItem("pending", "Pending task"),
                TodoItem("completed", "Completed task", TodoState.COMPLETED),
            )
        val port = protocolPort(todos)
        composeTestRule.setContent { MaterialTheme { TodoPanel(port, ComposeModalRequester { }, LifecycleState()) } }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Total 2").assertExists()
        composeTestRule.onNodeWithText("Pending task").assertExists()
        composeTestRule.onNodeWithText("Completed task").assertExists()
        composeTestRule.onNodeWithText("Pending").assertExists()
        composeTestRule.onNodeWithText("Completed").assertExists()
        assertEquals(2, composeTestRule.onAllNodesWithContentDescription("Edit todo").fetchSemanticsNodes().size)
        assertEquals(2, composeTestRule.onAllNodesWithContentDescription("Delete todo").fetchSemanticsNodes().size)
    }

    @Test
    fun `panel forwards start and stop all actions`() {
        val port = protocolPort(listOf(TodoItem("todo", "Task")))
        composeTestRule.setContent { MaterialTheme { TodoPanel(port, ComposeModalRequester { }, LifecycleState()) } }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Start all todos").performClick()
        composeTestRule.onNodeWithContentDescription("Stop all todos").performClick()

        io.mockk.verify(exactly = 1) { port.startAll() }
        io.mockk.verify(exactly = 1) { port.stopAll() }
    }

    @Test
    fun `todo add edit start stop complete and delete controls invoke their actions`() {
        val todo = TodoItem("todo", "Task")
        val port = protocolPort(listOf(todo))
        var requested: Any? = null
        composeTestRule.setContent {
            MaterialTheme { TodoPanel(port, ComposeModalRequester { requested = it }, LifecycleState()) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Add todo").performClick()
        assertEquals("Add todo", (requested as ComposeContentModal).title)
        composeTestRule.onNodeWithContentDescription("Edit todo").performClick()
        assertEquals("Edit todo", (requested as ComposeContentModal).title)
        composeTestRule.onNodeWithContentDescription("Start todo").performClick()
        composeTestRule.onNodeWithContentDescription("Stop todo").performClick()
        composeTestRule.onNodeWithContentDescription("Complete todo").performClick()
        composeTestRule.onNodeWithContentDescription("Delete todo").performClick()
        val confirmation = requested as ComposeConfirmationModal
        assertEquals("Delete todo?", confirmation.title)
        confirmation.onConfirm()

        io.mockk.verify(exactly = 1) { port.start("todo") }
        io.mockk.verify(exactly = 1) { port.stop("todo") }
        io.mockk.verify(exactly = 1) { port.updateStatus("todo", TodoState.COMPLETED) }
        io.mockk.verify(exactly = 1) { port.remove("todo") }
    }

    @Test
    fun `progress listener renders ordered partial responses before completion`() {
        var progressListener: ((TodoProgress) -> Unit)? = null
        var todoListener: ((TodoChange) -> Unit)? = null
        var currentTodo = TodoItem("todo", "Streaming task", TodoState.IN_PROGRESS)
        val port = protocolPort(listOf(currentTodo))
        every { port.list() } answers { listOf(currentTodo) }
        every { port.addListener(any()) } answers {
            todoListener = firstArg()
            AutoCloseable { }
        }
        every { port.addProgressListener(any()) } answers {
            progressListener = firstArg()
            AutoCloseable { }
        }
        composeTestRule.setContent { MaterialTheme { TodoPanel(port, ComposeModalRequester { }, LifecycleState()) } }

        composeTestRule.waitForIdle()
        progressListener!!.invoke(TodoProgress("todo", "New "))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New").assertExists()
        composeTestRule.onNodeWithContentDescription("Todo working").assertExists()
        progressListener!!.invoke(TodoProgress("todo", "response"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New response").assertExists()
        progressListener!!.invoke(TodoProgress("todo", completed = true))
        currentTodo = currentTodo.copy(status = TodoState.COMPLETED)
        todoListener!!.invoke(TodoChange(todo = currentTodo))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New response").assertDoesNotExist()
        assertEquals(0, composeTestRule.onAllNodesWithContentDescription("Todo working").fetchSemanticsNodes().size)
    }

    @Test
    fun `panel does not query server after shutdown starts`() {
        val lifecycle = LifecycleState()
        lifecycle.beginShutdown()
        val port = protocolPort(listOf(TodoItem("todo", "Task")))

        composeTestRule.setContent { MaterialTheme { TodoPanel(port, ComposeModalRequester { }, lifecycle) } }
        composeTestRule.waitForIdle()

        io.mockk.verify(exactly = 0) { port.list() }
    }

    private fun protocolPort(initial: List<TodoItem>): TodoPort {
        val port = mockk<TodoPort>(relaxed = true)
        every { port.list() } returns initial
        every { port.agents() } returns emptyList()
        every { port.addListener(any()) } returns AutoCloseable { }
        every { port.addProgressListener(any()) } returns AutoCloseable { }
        every { port.start(any()) } returns true
        every { port.stop(any()) } returns true
        return port
    }
}
