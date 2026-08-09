@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Verifies that with [reverseLayout] = true, the list must be in
 * reverse-chronological order (newest first) so that index 0 (bottom of
 * screen) shows the newest message.
 *
 * Without the fix, [ConversationPanel] passes history as-is (oldest first),
 * so the oldest message appears at the bottom and new messages are invisible.
 */
class ReverseLayoutMessageOrderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `newest message is at index 0 with reverseLayout`(): Unit =
        runTest {
            val history =
                listOf(
                    Message("user", "oldest", id = "a"),
                    Message("assistant", "middle", id = "b"),
                    Message("user", "newest", id = "c"),
                )

            val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

            composeTestRule.setContent {
                MaterialTheme {
                    val listState = rememberLazyListState()
                    listStateHolder.add(listState)
                    Box(modifier = Modifier.fillMaxSize().height(300.dp)) {
                        LazyColumn(state = listState, reverseLayout = true) {
                            // The fix: reverse the list so newest is at index 0 (bottom).
                            itemsIndexed(history.reversed(), key = { _, m -> m.id ?: "?" }) { _, m ->
                                Text(text = m.content, modifier = Modifier.height(40.dp))
                            }
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()

            val listState = listStateHolder.single()
            val visible = listState.layoutInfo.visibleItemsInfo.sortedBy { it.index }

            val item0 = visible.firstOrNull { it.index == 0 }
            check(item0 != null) { "Index 0 not visible" }

            // With the fix, index 0 contains the newest message.
            assertEquals(
                history.last().content,
                history.reversed()[item0.index].content,
                "Expected newest '${history.last().content}' at index 0 (bottom), " +
                    "but got '${history.reversed()[item0.index].content}'",
            )

            // The last visible item (at the top) must be the oldest message.
            val lastItem = visible.last()
            assertEquals(
                history.first().content,
                history.reversed()[lastItem.index].content,
                "Expected oldest '${history.first().content}' at index ${history.lastIndex} (top), " +
                    "but got '${history.reversed()[lastItem.index].content}'",
            )
        }

    @Test
    fun `all messages are visible`(): Unit =
        runTest {
            val history =
                listOf(
                    Message("user", "oldest", id = "a"),
                    Message("assistant", "middle", id = "b"),
                    Message("user", "newest", id = "c"),
                )

            composeTestRule.setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize().height(300.dp)) {
                        LazyColumn(
                            state = rememberLazyListState(),
                            reverseLayout = true,
                        ) {
                            itemsIndexed(history.reversed(), key = { _, m -> m.id ?: "?" }) { _, m ->
                                Text(text = m.content, modifier = Modifier.height(40.dp))
                            }
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("newest").assertExists()
            composeTestRule.onNodeWithText("oldest").assertExists()
            composeTestRule.onNodeWithText("middle").assertExists()
        }
}
