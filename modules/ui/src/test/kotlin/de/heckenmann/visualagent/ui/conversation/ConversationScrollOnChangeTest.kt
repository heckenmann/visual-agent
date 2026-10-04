@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

class ConversationScrollOnChangeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scrolls to bottom when a new message is appended`() {
        val messages: SnapshotStateList<Message> = (1..20).map { Message("user", "message $it") }.toMutableStateList()
        val listState = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        composeTestRule.setContent {
            val state = rememberLazyListState()
            listState.add(state)
            MaterialTheme {
                LazyColumn(
                    state = state,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(
                            text = messages[index].content,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                }
                ConversationScrollOnChangeEffect(
                    timeline = timeline(messages),
                    listState = state,
                )
            }
        }
        composeTestRule.waitForIdle()

        // Append a new message — should trigger scroll to bottom.
        messages.add(Message("assistant", "new message"))
        composeTestRule.waitForIdle()

        // With reverseLayout, newest items are at index 0.
        assertTrue(
            !listState.single().canScrollBackward,
            "expected canScrollBackward=false after new message appended",
        )
    }

    @Test
    fun `scrolls to bottom when a pending user message is displayed`() {
        val messages: SnapshotStateList<Message> = (1..20).map { Message("user", "message $it") }.toMutableStateList()
        val listState = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        val pendingUserMessage = mutableStateOf<String?>(null)
        composeTestRule.setContent {
            val state = rememberLazyListState()
            listState.add(state)
            MaterialTheme {
                LazyColumn(
                    state = state,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    pendingUserMessage.value?.let { pending ->
                        item(key = "pending-user") {
                            Text(text = pending, modifier = Modifier.padding(vertical = 20.dp))
                        }
                    }
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(
                            text = messages[index].content,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                }
                ConversationScrollOnChangeEffect(
                    timeline = timeline(messages, pending = pendingUserMessage.value),
                    listState = state,
                )
            }
        }
        composeTestRule.waitForIdle()

        kotlinx.coroutines.runBlocking { listState.single().scrollToItem(messages.lastIndex) }
        composeTestRule.waitForIdle()

        pendingUserMessage.value = "newly submitted message"
        composeTestRule.waitForIdle()

        val newestVisibleIndex =
            listState
                .single()
                .layoutInfo
                .visibleItemsInfo
                .firstOrNull()
                ?.index
        assertTrue(
            newestVisibleIndex == 0,
            "expected pending user message at index 0 to be visible after sending",
        )
    }

    @Test
    fun `scrolls to bottom when streaming assistant content changes`() {
        val messages: SnapshotStateList<Message> = (1..20).map { Message("user", "message $it") }.toMutableStateList()
        val listState = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        val streamingContent = mutableStateOf("")
        composeTestRule.setContent {
            val state = rememberLazyListState()
            listState.add(state)
            MaterialTheme {
                LazyColumn(
                    state = state,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    if (streamingContent.value.isNotEmpty()) {
                        item(key = "streaming-assistant") {
                            Text(text = streamingContent.value, modifier = Modifier.padding(vertical = 20.dp))
                        }
                    }
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(
                            text = messages[index].content,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                }
                ConversationScrollOnChangeEffect(
                    timeline = timeline(messages, stream = streamingContent.value),
                    listState = state,
                )
            }
        }
        composeTestRule.waitForIdle()

        kotlinx.coroutines.runBlocking { listState.single().scrollToItem(messages.lastIndex) }
        composeTestRule.waitForIdle()

        streamingContent.value = "partial assistant response"
        composeTestRule.waitForIdle()

        kotlinx.coroutines.runBlocking { listState.single().scrollToItem(messages.size) }
        composeTestRule.waitForIdle()

        streamingContent.value = "partial assistant response with additional streamed content"
        composeTestRule.waitForIdle()

        val newestVisibleIndex =
            listState
                .single()
                .layoutInfo
                .visibleItemsInfo
                .firstOrNull()
                ?.index
        assertTrue(
            newestVisibleIndex == 0,
            "expected streaming assistant message at index 0 to be visible",
        )
    }

    @Test
    fun `updated message content scrolls to latest without count increase`() {
        val messages: SnapshotStateList<Message> = (1..20).map { Message("user", "message $it") }.toMutableStateList()
        val listState = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        composeTestRule.setContent {
            val state = rememberLazyListState()
            listState.add(state)
            MaterialTheme {
                LazyColumn(
                    state = state,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(
                            text = messages[index].content,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                }
                ConversationScrollOnChangeEffect(timeline(messages), state)
            }
        }
        composeTestRule.waitForIdle()

        // Scroll to the end (oldest messages) to simulate user reading older messages.
        kotlinx.coroutines.runBlocking { listState.single().scrollToItem(messages.lastIndex) }
        composeTestRule.waitForIdle()

        // Update the last message content (e.g. streaming) without changing count.
        messages[messages.lastIndex] = Message("assistant", "very long streamed content that extends the last item")
        composeTestRule.waitForIdle()

        // Updated visible activity follows the same always-latest policy as inserted rows.
        val firstVisibleIndex =
            listState
                .single()
                .layoutInfo
                .visibleItemsInfo
                .firstOrNull()
                ?.index
                ?: -1
        assertTrue(
            firstVisibleIndex == 0,
            "expected new activity to return to the newest end even from browsed history",
        )
    }

    private fun timeline(
        history: List<Message>,
        pending: String? = null,
        stream: String = "",
    ) = buildConversationTimeline(
        history = history,
        pendingUserMessage = pending,
        pendingUserEntryId = "pending-user",
        streamingContent = stream,
        streamingEntryId = "streaming-assistant",
        requestActive = false,
        showOlderHistoryLoading = false,
        includeInlineComposer = false,
    )
}
