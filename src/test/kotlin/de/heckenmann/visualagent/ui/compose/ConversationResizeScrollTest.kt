@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ConversationResizeScrollTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scrolls to bottom after panel height changes`() {
        val panelSize = mutableStateOf(IntSize.Zero)
        val inputAreaHeight = mutableStateOf(0)
        val listState = composeConversation(panelSize, inputAreaHeight)

        kotlinx.coroutines.runBlocking { listState.single().scrollToItem(19) }
        composeTestRule.waitForIdle()

        panelSize.value = IntSize(200, 240)
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.waitForIdle()

        assertAtBottom(listState.single(), "expected panel height change to reveal the newest message")
    }

    @Test
    fun `scrolls to bottom after input area height changes`() {
        val panelSize = mutableStateOf(IntSize(200, 160))
        val inputAreaHeight = mutableStateOf(0)
        val listState = composeConversation(panelSize, inputAreaHeight)

        kotlinx.coroutines.runBlocking { listState.single().scrollToItem(19) }
        composeTestRule.waitForIdle()

        inputAreaHeight.value = 72
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.waitForIdle()

        assertAtBottom(listState.single(), "expected input area height change to reveal the newest message")
    }

    private fun composeConversation(
        panelSize: androidx.compose.runtime.MutableState<IntSize>,
        inputAreaHeight: androidx.compose.runtime.MutableState<Int>,
    ): MutableList<androidx.compose.foundation.lazy.LazyListState> {
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
                        Text(text = messages[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationResizeScrollEffect(
                    panelSize = panelSize.value,
                    inputAreaHeight = inputAreaHeight.value,
                    hasConversationContent = true,
                    listState = state,
                )
            }
        }
        composeTestRule.waitForIdle()
        return listState
    }

    private fun assertAtBottom(
        listState: androidx.compose.foundation.lazy.LazyListState,
        message: String,
    ) {
        assertTrue(!listState.canScrollBackward, message)
    }
}
