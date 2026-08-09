@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [LazyListState.scrollToBottom] with [reverseLayout] = true.
 *
 * With reverseLayout, newest items are at index 0, so scrollToBottom is
 * simply [LazyListState.scrollToItem]`(0)`. No retry loops or
 * [Int.MAX_VALUE] hacks are needed.
 */
class ComposeConversationPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scrollToBottom reaches index 0 after scrolling to end`(): Unit =
        runTest {
            val messages = (1..30).map { Message("user", "message-$it", id = "id-$it") }
            val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

            composeTestRule.setContent {
                MaterialTheme {
                    val listState = rememberLazyListState()
                    listStateHolder.add(listState)
                    // Short panel: only ~3 items fit.
                    Box(modifier = Modifier.height(80.dp)) {
                        LazyColumn(state = listState, reverseLayout = true) {
                            itemsIndexed(messages, key = { i, _ -> messages[i].id ?: "t-$i" }) { i, _ ->
                                Text(text = messages[i].content, modifier = Modifier.height(40.dp))
                            }
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()

            val listState = listStateHolder.single()

            // Scroll to the end (oldest messages) so we're not at the bottom.
            listState.scrollToItem(messages.lastIndex)
            composeTestRule.waitForIdle()

            // Verify we're not at the bottom (can scroll backward toward newer items).
            assertTrue(listState.canScrollBackward, "expected canScrollBackward=true after scrolling to end")

            // Call scrollToBottom on the test dispatcher.
            val job =
                backgroundScope.launch {
                    listState.scrollToBottom()
                }
            composeTestRule.mainClock.advanceTimeBy(16)
            composeTestRule.waitForIdle()
            job.join()

            // After scrollToBottom, we must be at the absolute bottom (index 0).
            assertFalse(listState.canScrollBackward, "expected canScrollBackward=false after scrollToBottom")

            // The first visible item must be index 0 (newest message).
            val visible = listState.layoutInfo.visibleItemsInfo
            val firstVisible = visible.firstOrNull()?.index ?: -1
            assertTrue(
                firstVisible == 0,
                "expected first visible to be index 0 (newest), but was $firstVisible",
            )
        }
}
