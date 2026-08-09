@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

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

class ConversationResizeScrollTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `keeps newest message visible after panel height changes while following latest`() {
        val panelSize = mutableStateOf(IntSize.Zero)
        val inputAreaHeight = mutableStateOf(0)
        val listState = composeConversation(panelSize, inputAreaHeight)

        panelSize.value = IntSize(200, 240)
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.waitForIdle()

        assertAtBottom(listState.single(), "expected panel height change to reveal the newest message")
    }

    @Test
    fun `preserves browsed history after input area height changes`() {
        val panelSize = mutableStateOf(IntSize(200, 160))
        val inputAreaHeight = mutableStateOf(0)
        val listState = composeConversation(panelSize, inputAreaHeight)

        kotlinx.coroutines.runBlocking { listState.single().scrollToItem(19) }
        composeTestRule.waitForIdle()
        val browsedIndex = listState.single().firstVisibleItemIndex
        val browsedOffset = listState.single().firstVisibleItemScrollOffset

        inputAreaHeight.value = 72
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.waitForIdle()

        assertEquals(browsedIndex, listState.single().firstVisibleItemIndex)
        assertEquals(browsedOffset, listState.single().firstVisibleItemScrollOffset)
    }

    @Test
    fun `user scroll during resize debounce is not overwritten`() {
        val panelSize = mutableStateOf(IntSize(200, 160))
        val inputAreaHeight = mutableStateOf(0)
        val listState = composeConversation(panelSize, inputAreaHeight)
        composeTestRule.mainClock.autoAdvance = false

        inputAreaHeight.value = 72
        composeTestRule.mainClock.advanceTimeByFrame()
        kotlinx.coroutines.runBlocking { listState.single().scrollToItem(8) }
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.waitForIdle()

        assertEquals(8, listState.single().firstVisibleItemIndex)
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
