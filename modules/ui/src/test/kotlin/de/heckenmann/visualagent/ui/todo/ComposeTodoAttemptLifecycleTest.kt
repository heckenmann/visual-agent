package de.heckenmann.visualagent.ui.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.LifecycleState
import de.heckenmann.visualagent.protocol.TodoChange
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoProgress
import de.heckenmann.visualagent.protocol.TodoResponseSnapshot
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.task.TaskAttemptResult
import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.TaskOutcome
import de.heckenmann.visualagent.task.WorkerHandoff
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/** Terminal and contract-edit events update an already open overlay without remounting the panel. */
class ComposeTodoAttemptLifecycleTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `live completion loads evidence and contract edits demote acceptance in the same overlay`() {
        var todo = TodoItem("todo", "Task", TodoState.IN_PROGRESS, contract = TaskContract(objective = "Original"))
        var attempt =
            TaskAttemptResult(
                "todo",
                "worker",
                "execution",
                1,
                1,
                "worker/1",
                WorkerHandoff("Verified output", TaskOutcome.PRODUCED),
                emptyList(),
                emptyList(),
                accepted = true,
                currentAccepted = true,
            )
        var changed: ((TodoChange) -> Unit)? = null
        var progress: ((TodoProgress) -> Unit)? = null
        val port = mockk<TodoPort>(relaxed = true)
        every { port.list() } answers { listOf(todo) }
        every { port.agents() } returns emptyList()
        every { port.addListener(any()) } answers {
            changed = firstArg()
            AutoCloseable { }
        }
        every { port.addProgressListener(any()) } answers {
            progress = firstArg()
            AutoCloseable { }
        }
        every { port.responseSnapshots(setOf("todo")) } answers {
            listOf(TodoResponseSnapshot("todo", "Verified output", attempts = listOf(attempt)))
        }
        var modal by mutableStateOf<ComposeContentModal?>(null)
        rule.setContent {
            MaterialTheme {
                Box {
                    TodoPanel(port, ComposeModalRequester { modal = it as? ComposeContentModal }, LifecycleState())
                    modal?.content { modal = null }
                }
            }
        }
        rule.waitForIdle()
        progress!!.invoke(TodoProgress("todo", "Live output", executionId = "execution", agentId = "worker"))
        rule.waitForIdle()
        rule.onNodeWithText("Live output").performClick()
        rule.waitForIdle()
        todo = todo.copy(status = TodoState.COMPLETED)
        progress!!.invoke(TodoProgress("todo", completed = true, executionId = "execution"))
        changed!!.invoke(TodoChange(todo = todo))
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithText("Attempt 1: Accepted result · PRODUCED").assertExists() }.isSuccess
        }
        attempt = attempt.copy(currentAccepted = false)
        todo = todo.copy(status = TodoState.PENDING, contract = TaskContract(revision = 2, objective = "Revised"))
        changed!!.invoke(TodoChange(todo = todo))
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithText("Attempt 1: Previously accepted result · PRODUCED").assertExists() }.isSuccess
        }
        rule.onNodeWithText("Attempt 1: Accepted result · PRODUCED").assertDoesNotExist()
    }
}
