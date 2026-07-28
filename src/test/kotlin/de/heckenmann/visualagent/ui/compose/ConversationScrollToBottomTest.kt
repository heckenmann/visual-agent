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
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationScrollToBottomArea(
                    isAtBottom = false,
                    history = messages,
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
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationScrollToBottomArea(
                    isAtBottom = true,
                    history = messages,
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

        val info = listStateHolder.single().layoutInfo
        val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        assertTrue(
            lastVisibleIndex >= messages.lastIndex,
            "expected last message to be visible after new message, but last visible index was $lastVisibleIndex of ${messages.size}",
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
                    modifier = Modifier.width(200.dp).height(160.dp),
                ) {
                    itemsIndexed(messages, key = { index, _ -> messages[index].id ?: "temp-$index" }) { index, _ ->
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationStartupScrollEffect(messages, listState)
            }
        }
        composeTestRule.waitForIdle()

        val info = listStateHolder.single().layoutInfo
        val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        assertTrue(
            lastVisibleIndex >= messages.lastIndex,
            "expected last message to be visible after startup scroll, but last visible index was $lastVisibleIndex",
        )
    }
}
