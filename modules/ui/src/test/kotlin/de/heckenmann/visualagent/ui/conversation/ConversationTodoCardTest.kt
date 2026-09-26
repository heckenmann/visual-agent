package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
