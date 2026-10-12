package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.ui.todo.TodoResponseState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies the canonical todo states remain visible as both status text and icon semantics. */
class ConversationTodoCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `every available state opens details without requiring response output`() {
        val opened = mutableListOf<String>()
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    TodoState.entries.forEach { status ->
                        ConversationTodoCard(
                            TodoItem(status.name, status.name, status),
                            TodoResponseState(),
                            false,
                            { opened += status.name },
                        )
                    }
                }
            }
        }
        TodoState.entries.forEach { status ->
            composeTestRule.onNodeWithText(status.name).performClick()
        }
        assertEquals(TodoState.entries.map { it.name }, opened)
    }

    @Test
    fun `focused card opens with Enter and Space`() {
        var opened = 0
        composeTestRule.setContent {
            MaterialTheme {
                ConversationTodoCard(TodoItem("keyboard", "Keyboard task"), TodoResponseState(), false, { opened++ })
            }
        }
        val card = composeTestRule.onNodeWithContentDescription("Todo: Keyboard task")
        card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        card.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        card.performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        assertEquals(2, opened)
    }

    @Test
    fun `deleted card retains its snapshot without opening missing details`() {
        var opened = 0
        composeTestRule.setContent {
            MaterialTheme {
                ConversationTodoCard(TodoItem("deleted", "Deleted task"), TodoResponseState(), true, { opened++ })
            }
        }
        composeTestRule.onNodeWithContentDescription("Todo: Deleted task").assertIsNotEnabled().performClick()
        composeTestRule.onNodeWithText("Unavailable").assertExists()
        assertEquals(0, opened)
    }

    @Test
    fun `reviewing todo shows its phase without discarding the response`() {
        val state =
            TodoResponseState().also {
                it.apply("execution", "worker", "Finished output", completed = true, reviewing = true)
            }
        composeTestRule.setContent {
            MaterialTheme {
                ConversationTodoCard(TodoItem("todo", "Write output", TodoState.IN_PROGRESS), state, false, {})
            }
        }
        composeTestRule.onNodeWithText("Reviewing result…").assertExists()
        composeTestRule.onNodeWithText("Finished output").assertExists()
    }

    @Test
    fun `each todo status displays its matching icon and readable label`() {
        val states = TodoState.entries
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    states.forEach { state ->
                        ConversationTodoCard(
                            todo = TodoItem("todo-${state.name}", "A ${state.name} task", status = state),
                            responseState = TodoResponseState(),
                            deleted = false,
                            onOpenResponse = {},
                        )
                    }
                }
            }
        }

        states.forEach { state ->
            val label =
                state.name
                    .lowercase()
                    .split('_')
                    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            composeTestRule.onNodeWithText(label, substring = false).assertExists()
            composeTestRule.onNodeWithContentDescription(label).assertExists()
        }

        assertEquals(Icons.Filled.Schedule, todoStatusIcon(TodoState.PENDING))
        assertEquals(Icons.Filled.PlayArrow, todoStatusIcon(TodoState.IN_PROGRESS))
        assertEquals(Icons.Filled.CheckCircle, todoStatusIcon(TodoState.COMPLETED))
        assertEquals(Icons.Filled.ErrorOutline, todoStatusIcon(TodoState.CANCELLED))
    }
}
