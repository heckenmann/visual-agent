package de.heckenmann.visualagent.ui.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/** Verifies layout details of the live todo streaming response. */
class TodoStreamingResponseLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `working indicator is vertically centered with streamed text`() {
        val responseState = TodoResponseState().apply { apply("execution", null, "Live response", completed = false) }
        composeTestRule.setContent {
            MaterialTheme {
                TodoStreamingResponse(
                    visible = true,
                    working = true,
                    responseState = responseState,
                    modalRequester = ComposeModalRequester { },
                    todo = TodoItem("todo", "Streaming task"),
                )
            }
        }

        val indicatorBounds = composeTestRule.onNodeWithContentDescription("Todo working").getUnclippedBoundsInRoot()
        val textBounds = composeTestRule.onNodeWithText("Live response").getUnclippedBoundsInRoot()

        val indicatorCenterY = (indicatorBounds.top + indicatorBounds.bottom) / 2f
        val textCenterY = (textBounds.top + textBounds.bottom) / 2f
        assertTrue(abs((indicatorCenterY - textCenterY).value) <= 0.5f)
    }

    @Test
    fun `streamed response text is aligned to the right edge`() {
        val responseState = TodoResponseState().apply { apply("execution", null, "Latest response", completed = false) }
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(240.dp)) {
                    TodoResponseSingleLine(
                        responseState = responseState,
                        modifier = Modifier.width(240.dp).semantics { testTag = "streamed-response" },
                    )
                }
            }
        }

        val containerBounds = composeTestRule.onNodeWithTag("streamed-response").getUnclippedBoundsInRoot()
        val textBounds = composeTestRule.onNodeWithText("Latest response").getUnclippedBoundsInRoot()

        assertTrue(abs((containerBounds.right - textBounds.right).value) <= 0.5f)
    }

    @Test
    fun `streamed response recomputes its visible suffix after panel resize`() {
        val responseState =
            TodoResponseState().apply {
                apply("execution", null, "OLDER_SEGMENT MIDDLE_SEGMENT END", completed = false)
            }
        var width by mutableStateOf(64.dp)
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(width)) {
                    TodoResponseSingleLine(
                        responseState = responseState,
                        modifier = Modifier.fillMaxWidth().semantics { testTag = "resizable-streamed-response" },
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("END", substring = true).assertExists()
        composeTestRule.onNodeWithText("MIDDLE_SEGMENT", substring = true).assertDoesNotExist()

        composeTestRule.runOnIdle { width = 320.dp }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("MIDDLE_SEGMENT", substring = true).assertExists()
        composeTestRule.onNodeWithText("END", substring = true).assertExists()
    }
}
