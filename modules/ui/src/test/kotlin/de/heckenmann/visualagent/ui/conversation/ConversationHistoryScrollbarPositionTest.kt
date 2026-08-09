@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that conversation paging keeps the shared list and scrollbar position aligned.
 */
class ConversationHistoryScrollbarPositionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `older history load preserves the scrollbar anchor after the new page is laid out`() {
        val initialHistory = (1..20).map { Message("user", "recent $it", id = "recent-$it") }
        val olderPage = (1..5).map { Message("user", "older $it", id = "older-$it") }
        val fullHistory = olderPage + initialHistory
        var history by mutableStateOf(initialHistory)
        var isAtEnd by mutableStateOf(false)
        val mockManager = mockk<AgentManager>(relaxed = true)
        every { mockManager.loadOlderHistory(any()) } returns olderPage
        every { mockManager.getHistory() } returns fullHistory
        val listStates = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStates.add(listState)
            MaterialTheme {
                PanelScrollbarHost(modifier = Modifier.width(200.dp).height(160.dp)) {
                    RegisterPanelScrollbar(rememberScrollbarAdapter(listState))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.width(200.dp).height(160.dp),
                        reverseLayout = true,
                    ) {
                        items(history.size) { index -> Text(history[index].content, modifier = Modifier.height(40.dp)) }
                    }
                    ConversationOlderHistoryLoader(
                        isAtEnd = isAtEnd,
                        history = history,
                        listState = listState,
                        agentManager = mockManager,
                        onHistoryChange = { history = it },
                    )
                    ConversationScrollOnChangeEffect(history, listState)
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Panel scrollbar").assertExists()

        composeTestRule.runOnIdle {
            kotlinx.coroutines.runBlocking { listStates.single().scrollToItem(initialHistory.lastIndex) }
            isAtEnd = true
        }
        val anchorBeforeReload = listStates.single().firstVisibleItemIndex

        composeTestRule.waitUntil(timeoutMillis = 5_000) { history.size == fullHistory.size }
        composeTestRule.waitForIdle()

        assertEquals(
            anchorBeforeReload,
            listStates.single().firstVisibleItemIndex,
            "the shared LazyListState must retain the stable message anchor when older items are appended",
        )
    }

    @Test
    fun `scroll to latest ignores an older history load that was already in progress`() {
        val initialHistory = (1..20).map { Message("user", "recent $it", id = "recent-$it") }
        val latestPage = (16..20).map { Message("user", "recent $it", id = "recent-$it") }
        val olderPage = (1..5).map { Message("user", "older $it", id = "older-$it") }
        var history by mutableStateOf(initialHistory)
        var isAtEnd by mutableStateOf(false)
        var paginationResetVersion by mutableStateOf(0)
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        val loadCalls = AtomicInteger(0)
        val mockManager = mockk<AgentManager>(relaxed = true)
        every { mockManager.loadOlderHistory(any()) } answers {
            if (loadCalls.incrementAndGet() == 1) {
                loadStarted.countDown()
                releaseLoad.await(5, TimeUnit.SECONDS)
            }
            olderPage
        }
        every { mockManager.getHistory() } returns latestPage
        val listStates = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            listStates.add(listState)
            MaterialTheme {
                PanelScrollbarHost(modifier = Modifier.width(200.dp).height(160.dp)) {
                    RegisterPanelScrollbar(rememberScrollbarAdapter(listState))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.width(200.dp).height(160.dp),
                        reverseLayout = true,
                    ) {
                        items(history.size) { index -> Text(history[index].content, modifier = Modifier.height(40.dp)) }
                    }
                    ConversationOlderHistoryLoader(
                        isAtEnd = isAtEnd,
                        history = history,
                        listState = listState,
                        agentManager = mockManager,
                        onHistoryChange = { history = it },
                        paginationResetVersion = paginationResetVersion,
                    )
                    ConversationScrollToBottomArea(
                        isAtBottom = false,
                        listState = listState,
                        scope = androidx.compose.runtime.rememberCoroutineScope(),
                        agentManager = mockManager,
                        onHistoryRefresh = {
                            history = latestPage
                            paginationResetVersion++
                        },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            kotlinx.coroutines.runBlocking { listStates.single().scrollToItem(initialHistory.lastIndex) }
            isAtEnd = true
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) { loadStarted.count == 0L }

        composeTestRule.onNodeWithContentDescription("Scroll to latest message").performClick()
        releaseLoad.countDown()
        composeTestRule.waitForIdle()

        assertEquals(0, listStates.single().firstVisibleItemIndex, "scroll to latest must remain at index 0")
        assertEquals(1, loadCalls.get(), "pagination reset must not load older history before scroll-down completes")
    }

    @Test
    fun `scroll to latest after reaching oldest keeps mouse wheel browsing available`() {
        val recentHistory = (41..60).map { Message("user", "recent $it", id = "recent-$it") }
        val olderLoadStarted = CompletableDeferred<Unit>()
        val releaseOlderLoad = CompletableDeferred<Unit>()
        val listStates = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val state = rememberConversationUiState(recentHistory)
            val coordinator = remember(listState) { ConversationScrollCoordinator(listState) }
            val viewport = remember(listState) { ConversationViewport(listState) }
            val userScrollConnection = remember(coordinator) { ConversationUserScrollConnection(coordinator) }
            val timeline = buildConversationTimeline(state.history, null, "", false, state.isLoadingOlder, false)
            val isAtLatest by remember { derivedStateOf { viewport.isAtLatest } }
            val gateway =
                remember {
                    object : ConversationHistoryGateway {
                        override suspend fun latest(): ConversationHistoryPage =
                            ConversationHistoryPage(recentHistory, offset = 0, hasMore = true)

                        override suspend fun older(offset: Int): ConversationHistoryPage {
                            olderLoadStarted.complete(Unit)
                            releaseOlderLoad.await()
                            return ConversationHistoryPage(emptyList(), offset = offset, hasMore = false)
                        }
                    }
                }
            listStates += listState
            MaterialTheme {
                Box(modifier = Modifier.width(200.dp).height(160.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(userScrollConnection)
                                .semantics { contentDescription = "Conversation history" },
                        reverseLayout = true,
                    ) {
                        itemsIndexed(state.history.reversed(), key = { _, message -> "message:${message.id}" }) { _, message ->
                            Text(message.content, modifier = Modifier.height(40.dp))
                        }
                    }
                    ConversationHistoryPagingEffect(
                        isAtOldest = viewport.isAtOldest,
                        state = state,
                        timeline = timeline,
                        listState = listState,
                        gateway = gateway,
                        scrollCoordinator = coordinator,
                    )
                    ConversationLatestPositionEffect(listState, coordinator)
                    ConversationScrollToLatestArea(
                        isAtLatest = isAtLatest,
                        state = state,
                        gateway = gateway,
                        scrollCoordinator = coordinator,
                        scope = rememberCoroutineScope(),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        kotlinx.coroutines.runBlocking { listStates.single().scrollToItem(recentHistory.lastIndex) }
        composeTestRule.waitUntil(timeoutMillis = 5_000) { olderLoadStarted.isCompleted }

        composeTestRule.onNodeWithContentDescription("Scroll to latest message").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { !listStates.last().canScrollBackward }
        composeTestRule.onNodeWithContentDescription("Conversation history").performMouseInput {
            moveTo(center)
            scroll(-10f)
        }
        composeTestRule.waitForIdle()

        assertTrue(
            listStates.last().canScrollBackward,
            "the user must be able to browse older messages after returning to the latest page",
        )
        releaseOlderLoad.complete(Unit)
    }
}
