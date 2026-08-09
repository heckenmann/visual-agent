@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
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
                    onInputChange = { inputState.value = it },
                    onSend = { sent = true },
                    onCancel = {},
                    onClear = {},
                    inputFocusRequester = FocusRequester(),
                )
            }
        }
        composeTestRule.onNodeWithText("Type a message…").performTextInput("hello")
        composeTestRule.onNodeWithContentDescription("Send message").performClick()
        assertTrue(sent)
        assertEquals("hello", currentInput)
    }

    @Test
    fun `send button is hidden while sending`() {
        var sent = false
        composeTestRule.setContent {
            MaterialTheme {
                ConversationInputArea(
                    input = "hi",
                    sending = true,
                    onInputChange = {},
                    onSend = { sent = true },
                    onCancel = {},
                    onClear = {},
                    inputFocusRequester = FocusRequester(),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Cancel response").assertExists()
        assertTrue(!sent)
    }

    @Test
    fun `cancel button is visible while sending and invokes onCancel`() {
        var cancelled = false
        composeTestRule.setContent {
            MaterialTheme {
                ConversationInputArea(
                    input = "hi",
                    sending = true,
                    onInputChange = {},
                    onSend = {},
                    onCancel = { cancelled = true },
                    onClear = {},
                    inputFocusRequester = FocusRequester(),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Cancel response").performClick()
        assertTrue(cancelled)
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
    fun `clear button invokes callback`() {
        var clearClicked = false
        composeTestRule.setContent {
            MaterialTheme {
                ConversationInputArea(
                    input = "",
                    sending = false,
                    onInputChange = {},
                    onSend = {},
                    onCancel = {},
                    onClear = { clearClicked = true },
                    inputFocusRequester = FocusRequester(),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Clear conversation").performClick()
        assertTrue(clearClicked)
    }
}
