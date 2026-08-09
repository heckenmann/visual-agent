@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Verifies user-priority arbitration in [ConversationScrollCoordinator]. */
class ConversationScrollCoordinatorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `user scroll invalidates a pending jump to latest`() {
        val states = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        composeTestRule.setContent {
            val state = rememberLazyListState()
            states += state
            MaterialTheme {
                Box(modifier = Modifier.width(200.dp).height(160.dp)) {
                    LazyColumn(state = state, reverseLayout = true) {
                        items((1..20).toList()) { index ->
                            Text("message $index", modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val state = states.single()
        runBlocking { state.scrollToItem(10) }
        composeTestRule.waitForIdle()
        val browsedIndex = state.firstVisibleItemIndex
        val coordinator = ConversationScrollCoordinator(state)
        val connection = ConversationUserScrollConnection(coordinator)
        val generation = runBlocking { coordinator.beginJumpToLatest() }

        connection.onPreScroll(Offset(0f, 24f), NestedScrollSource.UserInput)
        val completed = runBlocking { coordinator.completeJumpToLatest(generation) }

        assertFalse(completed)
        assertEquals(ConversationScrollMode.BROWSING_HISTORY, coordinator.mode)
        assertEquals(browsedIndex, state.firstVisibleItemIndex)
    }

    @Test
    fun `new content does not leave history browsing mode`() {
        val states = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        composeTestRule.setContent {
            val state = rememberLazyListState()
            states += state
            MaterialTheme {
                Box(modifier = Modifier.width(200.dp).height(160.dp)) {
                    LazyColumn(state = state, reverseLayout = true) {
                        items((1..20).toList()) { index ->
                            Text("message $index", modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val state = states.single()
        runBlocking { state.scrollToItem(10) }
        composeTestRule.waitForIdle()
        val browsedIndex = state.firstVisibleItemIndex
        val coordinator = ConversationScrollCoordinator(state)
        coordinator.onUserScrollStarted()
        coordinator.onUserScrollMoved()

        runBlocking { coordinator.followLatestContentChange() }

        assertEquals(ConversationScrollMode.BROWSING_HISTORY, coordinator.mode)
        assertEquals(browsedIndex, state.firstVisibleItemIndex)
    }

    @Test
    fun `user input at latest remains following until the list moves`() {
        val states = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        composeTestRule.setContent {
            val state = rememberLazyListState()
            states += state
            MaterialTheme {
                LazyColumn(
                    state = state,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    items((1..20).toList()) { index ->
                        Text("message $index", modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val state = states.single()
        val coordinator = ConversationScrollCoordinator(state)
        val connection = ConversationUserScrollConnection(coordinator)

        connection.onPreScroll(Offset(0f, 24f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(0f, 24f), Offset.Zero, NestedScrollSource.UserInput)

        assertEquals(ConversationScrollMode.FOLLOWING_LATEST, coordinator.mode)
    }

    @Test
    fun `first moved position after a stale latest snapshot enters browsing mode`() {
        val states = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        composeTestRule.setContent {
            val state = rememberLazyListState()
            states += state
            MaterialTheme {
                Box(modifier = Modifier.width(200.dp).height(160.dp)) {
                    LazyColumn(state = state, reverseLayout = true) {
                        items((1..20).toList()) { index ->
                            Text("message $index", modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val state = states.single()
        val coordinator = ConversationScrollCoordinator(state)

        runBlocking { state.scrollToItem(8) }
        composeTestRule.waitForIdle()
        runBlocking { coordinator.beginJumpToLatest() }
        runBlocking { state.scrollToItem(0) }
        coordinator.onUserScrollStarted()

        coordinator.updateLatestPosition(
            isAtLatest = true,
            position = ConversationListPosition(index = 0, offset = 0, canScrollBackward = false),
        )
        coordinator.updateLatestPosition(
            isAtLatest = false,
            position = ConversationListPosition(index = 1, offset = 0, canScrollBackward = true),
        )

        assertEquals(ConversationScrollMode.BROWSING_HISTORY, coordinator.mode)
    }
}
