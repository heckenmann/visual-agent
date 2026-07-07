@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the conversation input and scroll controls.
 */
class ConversationPanelControlsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `conversation input area reflects input and sends on click`() {
        var sent = false
        var currentInput = ""
        composeTestRule.setContent {
            val inputState = remember { mutableStateOf("") }
            currentInput = inputState.value
            MaterialTheme {
                ConversationInputArea(
                    input = inputState.value,
                    sending = false,
                    status = "Ready",
                    onInputChange = { inputState.value = it },
                    onSend = { sent = true },
                    onHistoryReload = {},
                    onClear = {},
                    inputFocusRequester = FocusRequester(),
                )
            }
        }
        composeTestRule.onNodeWithText("Message").performTextInput("hello")
        composeTestRule.onNodeWithContentDescription("Send message").performClick()
        assertTrue(sent)
        assertEquals("hello", currentInput)
    }

    @Test
    fun `send button is disabled while sending`() {
        var sent = false
        composeTestRule.setContent {
            MaterialTheme {
                ConversationInputArea(
                    input = "hi",
                    sending = true,
                    status = "Sending",
                    onInputChange = {},
                    onSend = { sent = true },
                    onHistoryReload = {},
                    onClear = {},
                    inputFocusRequester = FocusRequester(),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Send message").performClick()
        assertTrue(!sent)
    }

    @Test
    fun `scroll to bottom button invokes onClick`() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                ScrollToBottomButton(onClick = { clicked = true })
            }
        }
        composeTestRule.onNodeWithContentDescription("Scroll to latest message").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `history reload and clear buttons invoke callbacks`() {
        var historyClicked = false
        var clearClicked = false
        composeTestRule.setContent {
            MaterialTheme {
                ConversationInputArea(
                    input = "",
                    sending = false,
                    status = "Ready",
                    onInputChange = {},
                    onSend = {},
                    onHistoryReload = { historyClicked = true },
                    onClear = { clearClicked = true },
                    inputFocusRequester = FocusRequester(),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Load older history").performClick()
        composeTestRule.onNodeWithContentDescription("Clear conversation").performClick()
        assertTrue(historyClicked)
        assertTrue(clearClicked)
    }
}
