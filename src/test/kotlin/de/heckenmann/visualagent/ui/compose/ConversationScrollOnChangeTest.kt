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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ConversationScrollOnChangeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scrolls to bottom when last message content changes while size stays the same`() {
        val messages: SnapshotStateList<Message> = (1..20).map { Message("user", "message $it") }.toMutableStateList()
        val listState = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        composeTestRule.setContent {
            val state = rememberLazyListState()
            listState.add(state)
            MaterialTheme {
                LazyColumn(
                    state = state,
                    modifier = Modifier.width(200.dp).height(160.dp),
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

        // Simulate streaming response updating the last message content without adding a new item.
        messages[messages.lastIndex] = Message("assistant", "very long streamed content that extends the last item")

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        val info = listState.single().layoutInfo
        val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        assertTrue(
            lastVisibleIndex >= messages.lastIndex,
            "expected last message to remain visible after content change, but last visible index was $lastVisibleIndex of ${messages.size}",
        )
    }
}
