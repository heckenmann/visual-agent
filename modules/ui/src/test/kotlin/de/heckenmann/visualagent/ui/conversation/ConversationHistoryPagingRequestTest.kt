@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import de.heckenmann.visualagent.protocol.ConversationHistoryPage as ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/** Verifies that a loading-row viewport update does not cancel an older-page request. */
class ConversationHistoryPagingRequestTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `older history loads when the latest page contains only hidden tool turns`() {
        val recent = listOf(Message("assistant", "", id = "empty-turn", assistantToolTurn = true))
        val older = listOf(Message("user", "Older visible message", id = "older"))
        val loadedOffset = CompletableDeferred<Int>()

        composeTestRule.setContent {
            val state = rememberConversationUiState(recent)
            val listState = rememberLazyListState()
            val gateway =
                remember {
                    object : ConversationHistoryGateway {
                        override suspend fun latest(): ConversationHistoryPage = ConversationHistoryPage(recent, offset = 0, hasMore = true)

                        override suspend fun older(offset: Int): ConversationHistoryPage {
                            loadedOffset.complete(offset)
                            return ConversationHistoryPage(older, offset = offset, hasMore = false)
                        }
                    }
                }
            MaterialTheme {
                LazyColumn(state = listState, reverseLayout = true) {
                    conversationTimeline(
                        items =
                            buildConversationTimeline(
                                history = state.history,
                                pendingUserMessage = null,
                                streamingContent = "",
                                requestActive = false,
                                showOlderHistoryLoading = state.isLoadingOlder,
                                includeInlineComposer = false,
                            ),
                        sending = false,
                        deletingMessageIds = emptySet(),
                        onDeleteMessage = {},
                        onStatusChange = {},
                        onEditMessage = {},
                        sendContent = {},
                    )
                }
                ConversationHistoryPagingEffect(state, listState, gateway)
            }
        }

        composeTestRule.waitUntil { loadedOffset.isCompleted }
        assertEquals(recent.size, runBlocking { loadedOffset.await() })
        composeTestRule.waitUntil {
            composeTestRule.onAllNodesWithText("Older visible message").fetchSemanticsNodes().size == 1
        }
        composeTestRule.onNodeWithText("Older visible message").assertExists()
        composeTestRule.onNodeWithText("No conversation yet").assertDoesNotExist()
    }

    @Test
    fun `older history request survives the loader changing the oldest viewport state`() {
        val recentHistory = (11..20).map { Message("user", "recent $it", id = "recent-$it") }
        val olderHistory = (1..10).map { Message("user", "older $it", id = "older-$it") }
        val olderLoadStarted = CompletableDeferred<Unit>()
        val releaseOlderLoad = CompletableDeferred<Unit>()
        lateinit var observedState: ConversationUiState

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val state = rememberConversationUiState(recentHistory)
            observedState = state
            val gateway =
                remember {
                    object : ConversationHistoryGateway {
                        override suspend fun latest(): ConversationHistoryPage =
                            ConversationHistoryPage(recentHistory, offset = 0, hasMore = true)

                        override suspend fun older(offset: Int): ConversationHistoryPage {
                            olderLoadStarted.complete(Unit)
                            releaseOlderLoad.await()
                            return ConversationHistoryPage(olderHistory, offset = offset, hasMore = false)
                        }
                    }
                }
            LaunchedEffect(Unit) {
                listState.scrollToItem(recentHistory.lastIndex)
            }
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(state.history.reversed(), key = { _, message -> "message:${message.id}" }) { _, _ ->
                        Text("message", modifier = Modifier.height(40.dp))
                    }
                    if (state.isLoadingOlder) item(key = "loading-older") { Text("Loading", Modifier.height(40.dp)) }
                }
                ConversationHistoryPagingEffect(state, listState, gateway)
            }
        }
        runBlocking { olderLoadStarted.await() }
        composeTestRule.waitForIdle()

        assertTrue(observedState.isLoadingOlder, "a loader-driven viewport update must not cancel the older-history request")
        releaseOlderLoad.complete(Unit)
        composeTestRule.waitForIdle()
        assertTrue(observedState.history.size == 20)
    }
}
