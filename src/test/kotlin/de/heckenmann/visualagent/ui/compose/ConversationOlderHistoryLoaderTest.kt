@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationOlderHistoryLoaderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `loads older history when scrolled to top`() {
        val initialHistory = (1..5).map { Message("user", "msg $it", id = "id-$it") }
        val olderPage = (6..10).map { Message("user", "older $it", id = "id-$it") }
        val fullHistory = olderPage + initialHistory

        var history by mutableStateOf(initialHistory)
        var loadCalls = 0
        var loadingState = false
        var hasMoreHistory = true

        val mockManager = mockk<AgentManager>(relaxed = true)
        every { mockManager.loadOlderHistory(any()) } answers {
            loadCalls++
            if (loadCalls == 1) olderPage else emptyList()
        }
        every { mockManager.getHistory() } returns fullHistory

        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                ) {
                    items(history.size) { index ->
                        Text(text = history[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationOlderHistoryLoader(
                    isAtTop = true,
                    history = history,
                    listState = listState,
                    agentManager = mockManager,
                    onHistoryChange = { history = it },
                    onLoadStateChange = { loadingState = it },
                    onHasMoreHistoryChange = { hasMoreHistory = it },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        assertEquals(1, loadCalls, "loadOlderHistory should be called once when at top")
        assertEquals(fullHistory.size, history.size, "history should contain older + initial messages")
        assertFalse(loadingState, "loading should be false after completion")
    }

    @Test
    fun `sets hasMoreHistory to false when load returns empty list`() {
        val initialHistory = (1..3).map { Message("user", "msg $it", id = "id-$it") }
        var history by mutableStateOf(initialHistory)
        var loadCalls = 0
        var hasMoreHistory = true

        val mockManager = mockk<AgentManager>(relaxed = true)
        every { mockManager.loadOlderHistory(any()) } answers {
            loadCalls++
            emptyList()
        }
        every { mockManager.getHistory() } returns initialHistory

        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                ) {
                    items(history.size) { index ->
                        Text(text = history[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationOlderHistoryLoader(
                    isAtTop = true,
                    history = history,
                    listState = listState,
                    agentManager = mockManager,
                    onHistoryChange = { history = it },
                    onHasMoreHistoryChange = { hasMoreHistory = it },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        assertEquals(1, loadCalls, "loadOlderHistory should be called once")
        assertFalse(hasMoreHistory, "hasMoreHistory should be false when no older messages remain")
    }

    @Test
    fun `does not load when not at top`() {
        val initialHistory = (1..5).map { Message("user", "msg $it", id = "id-$it") }
        var history by mutableStateOf(initialHistory)
        var loadCalls = 0

        val mockManager = mockk<AgentManager>(relaxed = true)
        every { mockManager.loadOlderHistory(any()) } answers {
            loadCalls++
            emptyList()
        }

        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                ) {
                    items(history.size) { index ->
                        Text(text = history[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationOlderHistoryLoader(
                    isAtTop = false,
                    history = history,
                    listState = listState,
                    agentManager = mockManager,
                    onHistoryChange = { history = it },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        assertEquals(0, loadCalls, "loadOlderHistory should not be called when not at top")
    }

    @Test
    fun `does not load when history is empty`() {
        var history by mutableStateOf(emptyList<Message>())
        var loadCalls = 0

        val mockManager = mockk<AgentManager>(relaxed = true)
        every { mockManager.loadOlderHistory(any()) } answers {
            loadCalls++
            emptyList()
        }

        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                ) {
                    items(history.size) { index ->
                        Text(text = history[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationOlderHistoryLoader(
                    isAtTop = true,
                    history = history,
                    listState = listState,
                    agentManager = mockManager,
                    onHistoryChange = { history = it },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        assertEquals(0, loadCalls, "loadOlderHistory should not be called when history is empty")
    }

    @Test
    fun `does not re-fire after hasMoreHistory becomes false`() {
        val initialHistory = (1..3).map { Message("user", "msg $it", id = "id-$it") }
        var history by mutableStateOf(initialHistory)
        var loadCalls = 0
        var isAtTop by mutableStateOf(true)

        val mockManager = mockk<AgentManager>(relaxed = true)
        every { mockManager.loadOlderHistory(any()) } answers {
            loadCalls++
            emptyList()
        }
        every { mockManager.getHistory() } returns initialHistory

        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                ) {
                    items(history.size) { index ->
                        Text(text = history[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationOlderHistoryLoader(
                    isAtTop = isAtTop,
                    history = history,
                    listState = listState,
                    agentManager = mockManager,
                    onHistoryChange = { history = it },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        assertEquals(1, loadCalls, "loadOlderHistory should be called once")

        // Toggle isAtTop off and back on — should NOT re-fire because hasMoreHistory is false.
        isAtTop = false
        composeTestRule.waitForIdle()
        isAtTop = true
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        assertEquals(1, loadCalls, "loadOlderHistory should not be called again after hasMoreHistory became false")
    }

    @Test
    fun `filters duplicate messages from loaded page`() {
        val initialHistory = (1..3).map { Message("user", "msg $it", id = "id-$it") }
        // The loaded page includes id-1 which is already in history (a duplicate).
        val loadedPage =
            listOf(
                Message("user", "duplicate", id = "id-1"),
                Message("user", "older 4", id = "id-4"),
            )
        val fullHistory =
            listOf(
                Message("user", "older 4", id = "id-4"),
            ) + initialHistory

        var history by mutableStateOf(initialHistory)
        var loadCalls = 0

        val mockManager = mockk<AgentManager>(relaxed = true)
        every { mockManager.loadOlderHistory(any()) } answers {
            loadCalls++
            loadedPage
        }
        every { mockManager.getHistory() } returns fullHistory

        val listStateHolder = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStateHolder.add(listState)
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                ) {
                    items(history.size) { index ->
                        Text(text = history[index].content, modifier = Modifier.height(40.dp))
                    }
                }
                ConversationOlderHistoryLoader(
                    isAtTop = true,
                    history = history,
                    listState = listState,
                    agentManager = mockManager,
                    onHistoryChange = { history = it },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        assertEquals(1, loadCalls, "loadOlderHistory should be called once")
        // The duplicate (id-1) should not appear twice in the resulting history.
        val id1Count = history.count { it.id == "id-1" }
        assertEquals(1, id1Count, "duplicate message should appear only once")
        // The new message (id-4) should be prepended.
        assertTrue(history.any { it.id == "id-4" }, "new older message should be in history")
    }
}
