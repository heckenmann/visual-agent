@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ConversationScrollToBottomTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scroll-to-bottom button appears when not at bottom`() {
        val messages = (1..20).map { Message("user", "msg $it", id = "id-$it") }
        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationScrollToBottomArea(
                    isAtBottom = false,
                    listState = listState,
                    scope = rememberCoroutineScope(),
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Scroll to latest message").assertExists()
    }

    @Test
    fun `scroll-to-bottom button is hidden when at bottom`() {
        val messages = (1..5).map { Message("user", "msg $it", id = "id-$it") }
        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(300.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationScrollToBottomArea(
                    isAtBottom = true,
                    listState = listState,
                    scope = rememberCoroutineScope(),
                )
            }
        }
        composeTestRule.waitForIdle()
        // The button should not exist when at bottom.
        composeTestRule.onNodeWithContentDescription("Scroll to latest message").assertDoesNotExist()
    }

    @Test
    fun `conversation scroll-on-change scrolls to bottom when new message arrives`() {
        val messages: SnapshotStateList<Message> =
            (1..20).map { Message("user", "msg $it", id = "id-$it") }.toMutableStateList()
        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationScrollOnChangeEffect(messages, listState)
            }
        }
        composeTestRule.waitForIdle()

        // Add a new message — should trigger scroll to bottom.
        messages.add(Message("assistant", "new message", id = "id-21"))
        composeTestRule.waitForIdle()

        // With reverseLayout, newest items are at index 0.
        val info = listStateHolder.single().layoutInfo
        val firstVisibleIndex = info.visibleItemsInfo.firstOrNull()?.index ?: -1
        assertTrue(
            firstVisibleIndex == 0,
            "expected first visible item to be index 0 (newest message), but was $firstVisibleIndex",
        )
    }

    @Test
    fun `conversation scroll-on-change does not scroll when history is empty`() {
        val messages: SnapshotStateList<Message> = mutableStateListOf()
        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationScrollOnChangeEffect(messages, listState)
            }
        }
        composeTestRule.waitForIdle()
        // Should not crash and should not scroll.
        assertTrue(listStateHolder.single().firstVisibleItemIndex == 0)
    }

    @Test
    fun `conversation startup scroll goes to bottom`() {
        val messages = (1..20).map { Message("user", "msg $it", id = "id-$it") }
        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(200.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationStartupScrollEffect(messages, listState)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.waitForIdle()

        val state = listStateHolder.single()
        val info = state.layoutInfo
        // With reverseLayout, newest items are at index 0.
        val firstVisible = info.visibleItemsInfo.firstOrNull()
        assertTrue(
            firstVisible != null && firstVisible.index == 0,
            "expected first item (index 0, newest) to be visible, but first visible was ${firstVisible?.index}",
        )
    }

    @Test
    fun `clicking scroll-to-bottom button scrolls to the very last item`() {
        val messages = (1..20).map { Message("user", "msg $it", id = "id-$it") }
        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            val scope = rememberCoroutineScope()
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationScrollToBottomArea(
                    isAtBottom = false,
                    listState = listState,
                    scope = scope,
                )
            }
        }
        composeTestRule.waitForIdle()

        // Scroll to the end (oldest messages) so the button is visible.
        kotlinx.coroutines.runBlocking { listStateHolder.single().scrollToItem(messages.lastIndex) }
        composeTestRule.waitForIdle()

        // Click the scroll-to-bottom button.
        composeTestRule.onNodeWithContentDescription("Scroll to latest message").performClick()
        composeTestRule.waitForIdle()

        // After clicking, canScrollBackward should be false (fully scrolled to bottom).
        assertTrue(
            !listStateHolder.single().canScrollBackward,
            "expected canScrollBackward=false after clicking scroll-to-bottom button",
        )
    }

    @Test
    fun `scroll-to-bottom button invokes history refresh callback before scrolling`() {
        val messages = (1..20).map { Message("user", "msg $it", id = "id-$it") }
        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        var refreshCount = 0

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            val scope = rememberCoroutineScope()
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationScrollToBottomArea(
                    isAtBottom = false,
                    listState = listState,
                    scope = scope,
                    agentManager = null,
                    onHistoryRefresh = {
                        refreshCount++
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Scroll to the end (oldest messages) so the button is visible.
        kotlinx.coroutines.runBlocking { listStateHolder.single().scrollToItem(messages.lastIndex) }
        composeTestRule.waitForIdle()

        // Click the scroll-to-bottom button.
        composeTestRule.onNodeWithContentDescription("Scroll to latest message").performClick()
        composeTestRule.waitForIdle()

        // The refresh callback is always called on click (even when agentManager is
        // null, in which case the DB reload is skipped). This lets the production
        // panel update its local history state before scrolling.
        assertTrue(
            refreshCount == 1,
            "expected exactly one refresh callback, but got $refreshCount calls",
        )
        assertTrue(
            !listStateHolder.single().canScrollBackward,
            "expected canScrollBackward=false after clicking scroll-to-bottom button",
        )
    }
}
