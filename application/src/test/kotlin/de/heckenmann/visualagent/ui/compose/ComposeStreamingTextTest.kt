@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [StreamingText] progressive reveal behavior.
 *
 * Bug: when text changes rapidly (as during streaming), the animation
 * resets to 0 on every change, so nothing is ever displayed.
 */
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
    fun `animates to full text when animate is true`() {
        composeTestRule.setContent {
            MaterialTheme {
                StreamingText(text = "Hello world", animate = true, tickDelayMs = 1) { displayed ->
                    Text(text = displayed)
                }
            }
        }
        composeTestRule.waitUntil(1_000) {
            composeTestRule.onAllNodesWithText("Hello world").fetchSemanticsNodes().isNotEmpty()
        }
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

    @Test
    fun `shows full text after rapid updates — verifies streaming fix`() {
        var text by mutableStateOf("")
        composeTestRule.setContent {
            MaterialTheme {
                StreamingText(text = text, animate = true, tickDelayMs = 1) { displayed ->
                    Text(text = displayed)
                }
            }
        }
        // Simulate streaming: text grows in rapid chunks.
        // With the bug, each change resets visibleLength to 0 and nothing appears.
        text = "Hello"
        text = "Hello world"
        text = "Hello world, how"
        text = "Hello world, how are"
        text = "Hello world, how are you?"
        composeTestRule.waitUntil(1_000) {
            composeTestRule
                .onAllNodesWithText("Hello world, how are you?")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // With the fix, the full text is eventually displayed.
        // With the bug, nothing is displayed (visibleLength stays at 0).
        composeTestRule.onNodeWithText("Hello world, how are you?").assertExists()
    }
}
