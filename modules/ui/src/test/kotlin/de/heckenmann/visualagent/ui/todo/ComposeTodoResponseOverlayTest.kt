package de.heckenmann.visualagent.ui.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.ui.components.ImmediateMarkdownComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies that the todo response overlay exposes usable scrolling and dismissal controls. */
class ComposeTodoResponseOverlayTest {
    @get:Rule
    val composeTestRule: ComposeContentTestRule = ImmediateMarkdownComposeRule()

    @Test
    fun `close action dismisses the response overlay`() {
        var dismissed = false
        val responseState = TodoResponseState()
        responseState.apply("execution", "agent", "Response", completed = true)
        composeTestRule.setContent {
            MaterialTheme {
                TodoResponseOverlay(
                    todo = TodoItem(id = "todo", description = "Task"),
                    responseState = responseState,
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Close").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `response overlay exposes a vertical scrollbar`() {
        val responseState = TodoResponseState()
        responseState.apply("execution", "agent", (1..500).joinToString("\n\n"), completed = true)
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(800.dp)) {
                    TodoResponseOverlay(
                        todo = TodoItem(id = "todo", description = "Task"),
                        responseState = responseState,
                        onDismiss = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Modal scrollbar").assertExists()
    }

    @Test
    fun `response overlay renders Markdown while a todo is streaming`() {
        val responseState = TodoResponseState()
        responseState.apply("execution", "agent", "# Progress\n\n- first result\n", completed = false)
        composeTestRule.setContent {
            MaterialTheme {
                TodoResponseOverlay(
                    todo = TodoItem(id = "todo", description = "Task"),
                    responseState = responseState,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Progress", substring = true).assertExists()
        composeTestRule.onNodeWithText("first result", substring = true).assertExists()
        composeTestRule.onNodeWithText("# Progress", substring = true).assertDoesNotExist()
    }
}
