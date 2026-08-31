package de.heckenmann.visualagent.ui.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.composeModalHost
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies that the todo response overlay exposes usable scrolling and dismissal controls. */
class ComposeTodoResponseOverlayTest {
    @get:Rule
    val composeTestRule = createComposeRule()

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
        responseState.apply("execution", "agent", (1..500).joinToString("\n"), completed = true)
        composeTestRule.setContent {
            MaterialTheme {
                composeModalHost(
                    modal =
                        ComposeContentModal(title = "Todo response") {
                            TodoResponseOverlay(
                                todo = TodoItem(id = "todo", description = "Task"),
                                responseState = responseState,
                                onDismiss = {},
                            )
                        },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Modal scrollbar").assertExists()
    }
}
