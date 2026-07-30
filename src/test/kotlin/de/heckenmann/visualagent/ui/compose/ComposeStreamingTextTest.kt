@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ComposeStreamingTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays full text immediately when animate is false`() {
        composeTestRule.setContent {
            MaterialTheme {
                StreamingText(text = "Hello world", animate = false) { displayed ->
                    Text(text = displayed)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Hello world").assertExists()
    }

    @Test
    fun `animates word by word when animate is true`() {
        composeTestRule.setContent {
            MaterialTheme {
                StreamingText(text = "Hello world", animate = true, tickDelayMs = 1) { displayed ->
                    Text(text = displayed)
                }
            }
        }
        // Advance the clock to let the animation complete.
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Hello world").assertExists()
    }

    @Test
    fun `handles empty text`() {
        composeTestRule.setContent {
            MaterialTheme {
                StreamingText(text = "", animate = true) { displayed ->
                    Text(text = displayed)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").assertExists()
    }

    @Test
    fun `handles text with newlines`() {
        composeTestRule.setContent {
            MaterialTheme {
                StreamingText(text = "line1\nline2", animate = false) { displayed ->
                    Text(text = displayed)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("line1\nline2").assertExists()
    }
}
