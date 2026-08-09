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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies that a loading-row viewport update does not cancel an older-page request. */
class ConversationHistoryPagingRequestTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `older history request survives the loader changing the oldest viewport state`() {
        val recentHistory = (11..20).map { Message("user", "recent $it", id = "recent-$it") }
        val olderHistory = (1..10).map { Message("user", "older $it", id = "older-$it") }
        val olderLoadStarted = CompletableDeferred<Unit>()
        val releaseOlderLoad = CompletableDeferred<Unit>()
        var reachedOldest by mutableStateOf(false)
        lateinit var observedState: ConversationUiState

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val state = rememberConversationUiState(recentHistory)
            val coordinator = remember(listState) { ConversationScrollCoordinator(listState) }
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
            LaunchedEffect(state.isLoadingOlder) {
                if (state.isLoadingOlder) reachedOldest = false
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
                ConversationHistoryPagingEffect(reachedOldest, state, listState, gateway, coordinator)
            }
        }
        composeTestRule.runOnIdle { reachedOldest = true }
        composeTestRule.waitUntil(timeoutMillis = 5_000) { olderLoadStarted.isCompleted }
        composeTestRule.waitForIdle()

        assertTrue(observedState.isLoadingOlder, "a loader-driven viewport update must not cancel the older-history request")
        releaseOlderLoad.complete(Unit)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { observedState.history.size == 20 }
    }
}
