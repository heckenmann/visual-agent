package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationPreferences
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/** Verifies both composer placements in the complete conversation panel. */
class ConversationInputPlacementTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `empty prompt stays visible above the composer in a short panel`() {
        val conversationPort =
            mockk<ConversationPort>(relaxed = true) {
                every { preferences() } returns ConversationPreferences()
                coEvery { currentHistory() } returns emptyList()
                coEvery { latest() } returns ConversationHistoryPage(emptyList(), 0, false)
            }
        val settingsPort =
            mockk<SettingsPort>(relaxed = true) {
                coEvery { snapshotAsync() } returns SettingsSnapshot(followUpSuggestionsEnabled = false)
            }
        val activityPort = mockk<ActivityPort>(relaxed = true)
        val todoPort = mockk<TodoPort>(relaxed = true)
        val suggestionPort = mockk<ConversationSuggestionPort>(relaxed = true)
        val inFlight = InFlightStateHolder()
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 260.dp)) {
                    ConversationPanel(
                        modalRequester = ComposeModalRequester { },
                        inFlight = inFlight,
                        activityPort = activityPort,
                        todoPort = todoPort,
                        conversationPort = conversationPort,
                        suggestionPort = suggestionPort,
                        settingsPort = settingsPort,
                    )
                }
            }
        }

        fun assertPromptAbove(composerTop: androidx.compose.ui.unit.Dp) {
            val viewport = composeTestRule.onNodeWithContentDescription("Conversation history").getBoundsInRoot()
            val title = composeTestRule.onNodeWithText("No conversation yet").getBoundsInRoot()
            val body = composeTestRule.onNodeWithText("Write a message below to begin.").getBoundsInRoot()
            assertTrue(title.top >= viewport.top, "Empty prompt title is clipped above the conversation viewport")
            assertTrue(body.bottom <= composerTop, "Empty prompt is covered by the composer")
        }

        composeTestRule.waitForIdle()
        val pin = composeTestRule.onNodeWithContentDescription("Pin input field to panel")
        assertPromptAbove(pin.getBoundsInRoot().top)
        pin.performClick()
        composeTestRule.waitForIdle()
        assertPromptAbove(composeTestRule.onNodeWithTag("conversation-input-overlay").getBoundsInRoot().top)
    }

    @Test
    fun `inline input scrolls away while pinned input overlays scrolling messages`() {
        val history =
            (1..30).map { index ->
                Message(role = if (index % 2 == 0) "user" else "assistant", content = "message-$index", id = "id-$index")
            }
        val persistedPlacements = Channel<ConversationPreferences>(Channel.UNLIMITED)
        val conversationPort =
            mockk<ConversationPort>(relaxed = true) {
                every { preferences() } returns ConversationPreferences()
                every { updatePreferences(any()) } answers { persistedPlacements.trySend(firstArg()) }
                coEvery { currentHistory() } returns history
                coEvery { latest() } returns ConversationHistoryPage(history, 0, false)
            }
        val settingsPort =
            mockk<SettingsPort>(relaxed = true) {
                coEvery { snapshotAsync() } returns SettingsSnapshot(followUpSuggestionsEnabled = false)
            }
        val activityPort = mockk<ActivityPort>(relaxed = true)
        val todoPort = mockk<TodoPort>(relaxed = true)
        val suggestionPort = mockk<ConversationSuggestionPort>(relaxed = true)
        val inFlight = InFlightStateHolder()
        val historyRendered = CompletableDeferred<Unit>()
        lateinit var listState: LazyListState

        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(440.dp, 320.dp)) {
                    ConversationPanel(
                        modalRequester = ComposeModalRequester { },
                        inFlight = inFlight,
                        activityPort = activityPort,
                        todoPort = todoPort,
                        conversationPort = conversationPort,
                        suggestionPort = suggestionPort,
                        settingsPort = settingsPort,
                        onScrollStateObserved = { state, observed ->
                            listState = observed
                            if (state.history.size == history.size) historyRendered.complete(Unit)
                        },
                    )
                }
            }
        }

        runBlocking { historyRendered.await() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Pin input field to panel").assertIsDisplayed()

        runBlocking { listState.scrollToItem(15) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Pin input field to panel").assertDoesNotExist()
        runBlocking { listState.scrollToItem(0) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Pin input field to panel").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ConversationInputPlacement.FIXED, runBlocking { persistedPlacements.receive() }.inputPlacement)
        composeTestRule.onNodeWithTag("conversation-input-overlay").assertIsDisplayed()

        runBlocking { listState.scrollToItem(15) }
        composeTestRule.waitForIdle()
        val overlay = composeTestRule.onNodeWithTag("conversation-input-overlay").getBoundsInRoot()
        val viewport = composeTestRule.onNodeWithContentDescription("Conversation history").getBoundsInRoot()
        val overlayTop = with(composeTestRule.density) { (overlay.top - viewport.top).roundToPx() }
        val overlayBottom = with(composeTestRule.density) { (overlay.bottom - viewport.top).roundToPx() }

        assertTrue(
            listState.layoutInfo.visibleItemsInfo.any { item ->
                item.offset < overlayBottom && item.offset + item.size > overlayTop
            },
            "Conversation messages must occupy the space behind the input overlay while scrolling",
        )
        composeTestRule.onNodeWithTag("conversation-input-overlay").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Use conversation message input").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Use conversation message input").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ConversationInputPlacement.CONVERSATION_MESSAGE, runBlocking { persistedPlacements.receive() }.inputPlacement)
        composeTestRule.onNodeWithTag("conversation-input-overlay").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Pin input field to panel").assertIsDisplayed()
    }
}
