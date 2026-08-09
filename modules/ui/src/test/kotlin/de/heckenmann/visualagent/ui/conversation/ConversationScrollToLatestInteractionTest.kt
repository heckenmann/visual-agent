@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse

/** Verifies scroll-to-latest from a partially browsed conversation history. */
class ConversationScrollToLatestInteractionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scroll to latest reaches newest item from a partially browsed history`() {
        val fullHistory = (1..60).map { Message("user", "message $it", id = "message-$it") }
        val latestPage = (61..80).map { Message("user", "message $it", id = "message-$it") }
        val listStates = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val state = rememberConversationUiState(fullHistory)
            val coordinator = remember(listState) { ConversationScrollCoordinator(listState) }
            val viewport = remember(listState) { ConversationViewport(listState) }
            val userScrollConnection = remember(coordinator) { ConversationUserScrollConnection(coordinator) }
            val isAtLatest by remember { derivedStateOf { viewport.isAtLatest } }
            val gateway =
                remember {
                    object : ConversationHistoryGateway {
                        override suspend fun latest(): ConversationHistoryPage =
                            ConversationHistoryPage(latestPage, offset = 0, hasMore = true)

                        override suspend fun older(offset: Int): ConversationHistoryPage =
                            ConversationHistoryPage(emptyList(), offset = offset, hasMore = false)
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
        kotlinx.coroutines.runBlocking { listStates.single().scrollToItem(10) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Scroll to latest message").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { !listStates.last().canScrollBackward }

        assertFalse(listStates.last().canScrollBackward, "the button must land at the newest timeline item")
        composeTestRule.onNodeWithText("message 80").assertExists()
    }
}
