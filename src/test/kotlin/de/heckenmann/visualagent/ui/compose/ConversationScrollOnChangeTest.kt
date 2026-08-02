@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

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
import de.heckenmann.visualagent.agent.Message
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
                ConversationScrollOnChangeEffect(messages, state)
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
                    history = messages,
                    pendingUserMessage = pendingUserMessage.value,
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
                    history = messages,
                    listState = state,
                    streamingContent = streamingContent.value,
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
    fun `does not scroll when message content changes without count increase`() {
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
                ConversationScrollOnChangeEffect(messages, state)
            }
        }
        composeTestRule.waitForIdle()

        // Scroll to the end (oldest messages) to simulate user reading older messages.
        kotlinx.coroutines.runBlocking { listState.single().scrollToItem(messages.lastIndex) }
        composeTestRule.waitForIdle()
        val firstVisibleBefore = listState.single().firstVisibleItemIndex

        // Update the last message content (e.g. streaming) without changing count.
        messages[messages.lastIndex] = Message("assistant", "very long streamed content that extends the last item")
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // The scroll position should NOT have changed — the user's position is preserved.
        val firstVisibleAfter = listState.single().firstVisibleItemIndex
        assertTrue(
            firstVisibleAfter <= firstVisibleBefore + 1,
            "expected scroll position to be preserved when content changes without count increase, but first visible index changed from $firstVisibleBefore to $firstVisibleAfter",
        )
        // With reverseLayout, newest items are at index 0. The user scrolled to the end
        // (oldest messages), so the first visible item (index 0, newest) should NOT be visible.
        val firstVisibleIndex =
            listState
                .single()
                .layoutInfo
                .visibleItemsInfo
                .firstOrNull()
                ?.index
                ?: -1
        assertFalse(
            firstVisibleIndex == 0,
            "expected newest message (index 0) to NOT be visible (user should stay at their scroll position)",
        )
    }
}
