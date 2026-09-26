@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
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
        composeTestRule.onNodeWithText("Type here…").performTextInput("hello")
        composeTestRule.onNodeWithText("Message").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Send message").performClick()
        assertTrue(sent)
        assertEquals("hello", currentInput)
    }

    @Test
    fun `pin control switches between conversation and fixed panel placement`() {
        val placement = mutableStateOf(ConversationInputPlacement.CONVERSATION_MESSAGE)
        composeTestRule.setContent {
            MaterialTheme {
                ConversationInputArea(
                    input = "",
                    sending = false,
                    onInputChange = {},
                    onSend = {},
                    onCancel = {},
                    onClear = {},
                    inputPlacement = placement.value,
                    onInputPlacementChange = { placement.value = it },
                    inputFocusRequester = FocusRequester(),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Pin input field to panel").performClick()
        composeTestRule.runOnIdle { assertEquals(ConversationInputPlacement.FIXED, placement.value) }
        composeTestRule.onNodeWithContentDescription("Use conversation message input").performClick()
        composeTestRule.runOnIdle { assertEquals(ConversationInputPlacement.CONVERSATION_MESSAGE, placement.value) }
    }

    @Test
    fun `composer actions have accessible hit targets`() {
        val sending = mutableStateOf(false)
        composeTestRule.setContent {
            MaterialTheme {
                ConversationInputArea(
                    input = "hello",
                    sending = sending.value,
                    onInputChange = {},
                    onSend = {},
                    onCancel = {},
                    onClear = {},
                    inputFocusRequester = FocusRequester(),
                )
            }
        }

        fun assertTarget(description: String) {
            val bounds = composeTestRule.onNodeWithContentDescription(description).getBoundsInRoot()
            assertTrue(
                bounds.right - bounds.left >= 40.dp && bounds.bottom - bounds.top >= 40.dp,
                "$description target is smaller than 40 dp",
            )
        }

        assertTarget("Clear conversation")
        assertTarget("Pin input field to panel")
        assertTarget("Send message")

        composeTestRule.runOnIdle { sending.value = true }
        assertTarget("Cancel response")
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
    fun `context reduction shows warning and emphasizes the send action`() {
        composeTestRule.setContent {
            MaterialTheme {
                ConversationInputArea(
                    input = "Follow up",
                    sending = false,
                    contextReduced = true,
                    onInputChange = {},
                    onSend = {},
                    onCancel = {},
                    onClear = {},
                    inputFocusRequester = FocusRequester(),
                )
            }
        }

        composeTestRule.onNodeWithText("Recent context or tool details were omitted to fit the model's token limit.").assertExists()
        composeTestRule.onNodeWithContentDescription("Send message; context was reduced").assertExists()
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
    fun `scroll control identifies unread messages while browsing`() {
        composeTestRule.setContent {
            MaterialTheme {
                ScrollToBottomButton(onClick = {}, hasNewMessages = true)
            }
        }

        composeTestRule.onNodeWithText("New messages").assertExists()
        composeTestRule.onNodeWithContentDescription("Show new messages").assertExists()
    }

    @Test
    fun `scroll to latest button stays above the conversation composer`() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 260.dp)) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(112.dp)
                            .testTag("conversation-composer"),
                    )
                    ConversationPanelScrollToLatest(
                        isAtLatest = false,
                        hasNewMessages = false,
                        state = rememberConversationUiState(emptyList()),
                        gateway =
                            object : ConversationHistoryGateway {
                                override suspend fun latest() = ConversationHistoryPage(emptyList(), offset = 0, hasMore = false)

                                override suspend fun older(offset: Int) = ConversationHistoryPage(emptyList(), offset, false)
                            },
                        listState = rememberLazyListState(),
                        scope = rememberCoroutineScope(),
                        bottomInset = 112.dp,
                    )
                }
            }
        }

        val composerBounds = composeTestRule.onNodeWithTag("conversation-composer").getBoundsInRoot()
        val scrollButtonBounds = composeTestRule.onNodeWithContentDescription("Scroll to latest message").getBoundsInRoot()
        assertTrue(scrollButtonBounds.bottom <= composerBounds.top)
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

    @Test
    fun `queueing a message clears the input after the queue accepts it`() {
        var input = "Follow up"
        var status = ""
        val queue = MessageQueue()

        queueUserMessage(
            content = input,
            enqueue = { message -> queue.enqueue(message, QueuedMessageSource.USER) },
            queuedMessageCount = { queue.size },
            onInputChange = { input = it },
            onStatusChange = { status = it },
        )

        assertEquals("", input)
        assertEquals("Follow up", queue.peek()?.content)
        assertEquals("Queued (1)", status)
    }

    @Test
    fun `queueing failure keeps the input for retry`() {
        var input = "Follow up"
        var status = ""

        queueUserMessage(
            content = input,
            enqueue = { error("Queue unavailable") },
            queuedMessageCount = { 0 },
            onInputChange = { input = it },
            onStatusChange = { status = it },
        )

        assertEquals("Follow up", input)
        assertTrue(status.startsWith("Could not queue message:"))
    }
}
