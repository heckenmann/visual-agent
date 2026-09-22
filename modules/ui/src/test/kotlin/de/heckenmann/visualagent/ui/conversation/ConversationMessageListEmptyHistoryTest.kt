@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
import kotlin.test.assertIs
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/**
 * Verifies that [conversationMessageList] keeps transient conversation rows in its list when the
 * history list is empty.
 *
 * Bug: [conversationMessageList] returns early when history is empty,
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
                            conversationMessageList(
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
                                pendingUserEntryId = PENDING_ENTRY_ID,
                            )
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()

            val listInfo = listStateHolder.single().layoutInfo
            assertEquals(2, listInfo.totalItemsCount)
            assertEquals(0, listInfo.visibleItemsInfo.first().index)
            composeTestRule.onNodeWithText("Thinking").assertExists()
        }

    @Test
    fun `timeline creates streaming content when history is empty`() {
        val items =
            buildConversationTimeline(
                history = emptyList(),
                pendingUserMessage = null,
                streamingContent = "I'm thinking...",
                showWaitingIndicator = false,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
                streamingEntryId = STREAMING_ENTRY_ID,
            )

        val streaming = assertIs<ConversationTimelineItem.MessageEntry>(items.single())
        assertEquals("assistant", streaming.message.role)
        assertEquals("I'm thinking...", streaming.message.content)
        assertEquals(STREAMING_ENTRY_ID, streaming.stableKey)
    }

    @Test
    fun `timeline keeps pending user and streaming content when history is empty`() {
        val items =
            buildConversationTimeline(
                history = emptyList(),
                pendingUserMessage = "Hello!",
                streamingContent = "Streaming response...",
                showWaitingIndicator = false,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
                pendingUserEntryId = PENDING_ENTRY_ID,
                streamingEntryId = STREAMING_ENTRY_ID,
            ).filterIsInstance<ConversationTimelineItem.MessageEntry>()

        assertEquals(listOf("Streaming response...", "Hello!"), items.map { it.message.content })
        assertEquals(listOf(STREAMING_ENTRY_ID, PENDING_ENTRY_ID), items.map { it.stableKey })
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
                            conversationMessageList(
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
                                pendingUserEntryId = PENDING_ENTRY_ID,
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
                            conversationMessageList(
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

    private companion object {
        const val PENDING_ENTRY_ID = "11111111-1111-4111-8111-111111111111"
        const val STREAMING_ENTRY_ID = "22222222-2222-4222-8222-222222222222"
    }
}
