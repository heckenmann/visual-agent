@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
                                inFlight = InFlightStateHolder(),
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
            assertEquals(1, listInfo.totalItemsCount)
            assertEquals(0, listInfo.visibleItemsInfo.single().index)
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
                composeTestRule
                    .onAllNodesWithText("Streaming response...")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeTestRule.onNodeWithText("Hello!").assertExists()
            composeTestRule.onNodeWithText("Streaming response...").assertExists()
        }
}
