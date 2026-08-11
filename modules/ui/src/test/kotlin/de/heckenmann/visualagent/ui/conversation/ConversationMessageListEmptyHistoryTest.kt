@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message
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
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Verifies that [ConversationMessageList] renders pending user messages and
 * streaming content even when the history list is empty.
 *
 * Bug: [ConversationMessageList] returns early when history is empty,
 * showing only "No conversation yet". The pendingUserMessage and
 * streamingContent blocks are inside the else branch and never reached.
 * This means the user sees nothing until the full response is complete.
 */
class ConversationMessageListEmptyHistoryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows pending user message when history is empty`(): Unit =
        runTest {
            val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
            val inFlight = InFlightStateHolder().also { it.markStreamStart("request-1") }
            composeTestRule.setContent {
                MaterialTheme {
                    val listState = rememberLazyListState()
                    listStateHolder += listState
                    Box(modifier = Modifier.height(300.dp)) {
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                        ) {
                            ConversationMessageList(
                                history = emptyList(),
                                sending = true,
                                inFlight = inFlight,
                                pendingUserMessage = "Hello, agent!",
                                streamingContent = "",
                                deletingMessageIds = emptySet(),
                                onDeleteMessage = {},
                                onStatusChange = {},
                                onEditMessage = {},
                                sendContent = {},
                            )
                        }
                    }
                }
            }
            composeTestRule.mainClock.advanceTimeBy(100)
            composeTestRule.waitForIdle()

            val listInfo = listStateHolder.single().layoutInfo
            assertEquals(2, listInfo.totalItemsCount)
            assertEquals(0, listInfo.visibleItemsInfo.first().index)
            composeTestRule.onNodeWithText("Thinking").assertExists()
            composeTestRule.onNodeWithContentDescription("You avatar").assertExists()
            composeTestRule.onNodeWithContentDescription("Copy user message").assertExists()
        }

    @Test
    fun `shows streaming content when history is empty`(): Unit =
        runTest {
            composeTestRule.setContent {
                MaterialTheme {
                    Box(modifier = Modifier.height(300.dp)) {
                        LazyColumn(
                            state = rememberLazyListState(),
                            reverseLayout = true,
                        ) {
                            ConversationMessageList(
                                history = emptyList(),
                                sending = true,
                                inFlight = InFlightStateHolder(),
                                pendingUserMessage = null,
                                streamingContent = "I'm thinking...",
                                deletingMessageIds = emptySet(),
                                onDeleteMessage = {},
                                onStatusChange = {},
                                onEditMessage = {},
                                sendContent = {},
                            )
                        }
                    }
                }
            }
            composeTestRule.waitUntil(1_000) {
                composeTestRule.onAllNodesWithText("I'm thinking...").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("I'm thinking...").assertExists()
        }

    @Test
    fun `shows both pending user message and streaming content when history is empty`(): Unit =
        runTest {
            composeTestRule.setContent {
                MaterialTheme {
                    Box(modifier = Modifier.height(300.dp)) {
                        LazyColumn(
                            state = rememberLazyListState(),
                            reverseLayout = true,
                        ) {
                            ConversationMessageList(
                                history = emptyList(),
                                sending = true,
                                inFlight = InFlightStateHolder(),
                                pendingUserMessage = "Hello!",
                                streamingContent = "Streaming response...",
                                deletingMessageIds = emptySet(),
                                onDeleteMessage = {},
                                onStatusChange = {},
                                onEditMessage = {},
                                sendContent = {},
                            )
                        }
                    }
                }
            }
            composeTestRule.waitUntil(1_000) {
                composeTestRule.onAllNodesWithText("Hello!").fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule
                        .onAllNodesWithText("Streaming response...")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
            }

            composeTestRule.onNodeWithText("Hello!").assertExists()
            composeTestRule.onNodeWithText("Streaming response...").assertExists()
            composeTestRule.onNodeWithText("Thinking").assertDoesNotExist()
        }

    @Test
    fun `shows waiting indicator at newest end with existing history`(): Unit =
        runTest {
            val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
            val inFlight = InFlightStateHolder().also { it.markStreamStart("request-1") }
            composeTestRule.setContent {
                MaterialTheme {
                    val listState = rememberLazyListState()
                    listStateHolder += listState
                    Box(modifier = Modifier.height(300.dp)) {
                        LazyColumn(state = listState, reverseLayout = true) {
                            ConversationMessageList(
                                history =
                                    listOf(
                                        Message(
                                            "assistant",
                                            "Earlier response",
                                            id = "assistant-1",
                                        ),
                                    ),
                                sending = true,
                                inFlight = inFlight,
                                pendingUserMessage = "Follow-up",
                                streamingContent = "",
                                deletingMessageIds = emptySet(),
                                onDeleteMessage = {},
                                onStatusChange = {},
                                onEditMessage = {},
                                sendContent = {},
                            )
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Thinking").assertExists()
            val newestVisibleIndex =
                listStateHolder
                    .single()
                    .layoutInfo
                    .visibleItemsInfo
                    .first()
                    .index
            assertEquals(0, newestVisibleIndex)
        }

    @Test
    fun `shows waiting indicator for an active tool without streaming text`(): Unit =
        runTest {
            val inFlight = InFlightStateHolder()
            inFlight.state.value = InFlightState(pendingToolIds = setOf("file:read"))
            composeTestRule.setContent {
                MaterialTheme {
                    Box(modifier = Modifier.height(300.dp)) {
                        LazyColumn(state = rememberLazyListState(), reverseLayout = true) {
                            ConversationMessageList(
                                history =
                                    listOf(
                                        Message(
                                            "assistant",
                                            "Earlier response",
                                            id = "assistant-1",
                                        ),
                                    ),
                                sending = false,
                                inFlight = inFlight,
                                pendingUserMessage = null,
                                streamingContent = "",
                                deletingMessageIds = emptySet(),
                                onDeleteMessage = {},
                                onStatusChange = {},
                                onEditMessage = {},
                                sendContent = {},
                            )
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Thinking").assertExists()
        }
}
