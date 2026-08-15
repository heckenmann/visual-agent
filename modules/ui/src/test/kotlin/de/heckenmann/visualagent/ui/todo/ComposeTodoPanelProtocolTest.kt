package de.heckenmann.visualagent.ui.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoProgress
import de.heckenmann.visualagent.protocol.TodoState
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
        composeTestRule.setContent { MaterialTheme { TodoPanel(port, ComposeModalRequester { }) } }

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
        composeTestRule.setContent { MaterialTheme { TodoPanel(port, ComposeModalRequester { }) } }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Start all todos").performClick()
        composeTestRule.onNodeWithContentDescription("Stop all todos").performClick()

        io.mockk.verify(exactly = 1) { port.startAll() }
        io.mockk.verify(exactly = 1) { port.stopAll() }
    }

    @Test
    fun `progress listener renders transient response only for active todo`() {
        var progressListener: ((TodoProgress) -> Unit)? = null
        val port = protocolPort(listOf(TodoItem("todo", "Streaming task", TodoState.IN_PROGRESS)))
        every { port.addProgressListener(any()) } answers {
            progressListener = firstArg()
            AutoCloseable { }
        }
        composeTestRule.setContent { MaterialTheme { TodoPanel(port, ComposeModalRequester { }) } }

        composeTestRule.waitForIdle()
        progressListener!!.invoke(TodoProgress("todo", "New response"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New response").assertExists()
        progressListener!!.invoke(TodoProgress("todo", completed = true))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New response").assertDoesNotExist()
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
