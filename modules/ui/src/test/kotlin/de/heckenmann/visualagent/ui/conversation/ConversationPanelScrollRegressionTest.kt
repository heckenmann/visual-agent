@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.config.ConversationInputPlacement
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
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
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises scroll-to-latest through the complete production conversation panel. */
@org.junit.experimental.categories.Category(de.heckenmann.visualagent.testsupport.DatabaseTestCategory::class)
class ConversationPanelScrollRegressionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `inline composer reaches new database messages and remains scrollable`() {
        verifyScrollToLatest(ConversationInputPlacement.CONVERSATION_MESSAGE)
    }

    @Test
    fun `fixed composer reaches new database messages and remains scrollable`() {
        verifyScrollToLatest(ConversationInputPlacement.FIXED)
    }

    @Test
    fun `latest button cancels an active wheel scroll before jumping`() {
        verifyScrollToLatest(ConversationInputPlacement.FIXED, settleBeforeClick = false)
    }

    @Test
    fun `inline composer is reached from fully loaded oldest history and remains scrollable`() {
        verifyScrollToLatest(ConversationInputPlacement.CONVERSATION_MESSAGE, browseToOldest = true)
    }

    @Test
    fun `reopened inline panel starts at the composer below the latest message`() {
        verifyScrollToLatest(ConversationInputPlacement.CONVERSATION_MESSAGE, startupOnly = true)
    }

    @Test
    fun `reopened fixed panel starts at the latest message`() {
        verifyScrollToLatest(ConversationInputPlacement.FIXED, startupOnly = true)
    }

    private fun verifyScrollToLatest(
        placement: ConversationInputPlacement,
        settleBeforeClick: Boolean = true,
        browseToOldest: Boolean = false,
        startupOnly: Boolean = false,
    ) {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val toolEventBus = ToolEventBus()
        val todoEventBus = TodoEventBus()
        val config = AppConfigBean(db).also { it.conversationInputPlacement = placement }
        val inFlight = InFlightStateHolder()
        val modalRequester = ComposeModalRequester { }
        lateinit var observedState: ConversationUiState
        lateinit var observedListState: LazyListState
        if (startupOnly) {
            saveMessages(db, 1..80, withVariableMarkdown = true)
        }
        val manager =
            AgentManager(
                db,
                mockk<LLMProvider>(relaxed = true),
                AgentToolConfigService(db),
                toolEventBus,
                todoEventBus,
                config,
            )
        if (!startupOnly) {
            saveMessages(db, 1..80)
            manager.refreshHistoryToLatest()
            manager.loadOlderHistory()
            manager.loadOlderHistory()
        }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(480.dp).height(600.dp)) {
                    PanelScrollbarHost(modifier = Modifier.fillMaxSize()) {
                        ConversationPanel(
                            agentManager = manager,
                            modalRequester = modalRequester,
                            inFlight = inFlight,
                            toolEventBus = toolEventBus,
                            todoEventBus = todoEventBus,
                            config = config,
                            onScrollStateObserved = { state, listState ->
                                observedState = state
                                observedListState = listState
                            },
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        waitUntilWithFrames {
            composeTestRule.onAllNodesWithContentDescription("Conversation history").fetchSemanticsNodes().isNotEmpty()
        }
        repeat(30) { composeTestRule.mainClock.advanceTimeByFrame() }
        assertLatestPosition(placement, observedListState)
        composeTestRule.onNodeWithText("panel message 80", substring = true).assertIsDisplayed()
        if (startupOnly) return

        scrollHistory(if (browseToOldest) -10_000f else -8f)
        if (browseToOldest) {
            waitUntilWithFrames { observedState.history.size == 80 }
            scrollHistory(-10_000f)
            waitUntilWithFrames { !observedState.hasMoreHistory }
        }
        waitUntilWithFrames { scrollToLatestButtonExists() }
        if (settleBeforeClick) {
            waitUntilWithFrames { !observedListState.isScrollInProgress }
        }
        saveMessages(db, 81..85)

        composeTestRule.onNodeWithContentDescription("Scroll to latest message").performClick()
        waitUntilWithFrames {
            observedState.history.any { it.content == "panel message 85" }
        }
        repeat(30) { composeTestRule.mainClock.advanceTimeByFrame() }
        assertLatestPosition(placement, observedListState)
        waitUntilWithFrames {
            composeTestRule.onAllNodesWithContentDescription("Scroll to latest message").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("panel message 85").assertIsDisplayed()
        if (placement == ConversationInputPlacement.CONVERSATION_MESSAGE) {
            composeTestRule.onNodeWithText("Type a message…").assertIsDisplayed()
        }
        composeTestRule.onNodeWithContentDescription("Conversation history").performMouseInput {
            moveTo(center)
            scroll(-8f)
        }
        waitUntilWithFrames {
            observedListState.firstVisibleItemIndex > 0 || observedListState.firstVisibleItemScrollOffset > 0
        }
        assertTrue(scrollToLatestButtonExists(), "manual browsing after the jump must reveal the latest-message button")
        if (browseToOldest) {
            val latestPageSize = observedState.history.size
            scrollHistoryToOldest()
            composeTestRule.onNodeWithText("panel message 1").assertIsDisplayed()
            assertEquals(
                latestPageSize,
                observedState.history.size,
                "scrolling back to the oldest message must reuse the history preserved by the latest jump",
            )
            assertTrue(
                observedState.history.any { it.content == "panel message 65" },
                "the latest jump must retain already loaded older history",
            )
        }
    }

    private fun waitUntilWithFrames(condition: () -> Boolean) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.mainClock.advanceTimeByFrame()
            condition()
        }
    }

    private fun scrollHistory(delta: Float) {
        composeTestRule.onNodeWithContentDescription("Conversation history").performMouseInput {
            moveTo(center)
            scroll(delta)
        }
    }

    private fun scrollHistoryToOldest() {
        repeat(200) {
            scrollHistory(-100f)
            composeTestRule.mainClock.advanceTimeByFrame()
        }
    }

    private fun saveMessages(
        db: de.heckenmann.visualagent.knowledge.PersistenceStores,
        indices: IntRange,
        withVariableMarkdown: Boolean = false,
    ) {
        indices.forEach { index ->
            val content =
                if (withVariableMarkdown) {
                    "panel message $index\n\n## Details\n\n" + List(index % 4 + 1) { "- history line $it" }.joinToString("\n")
                } else {
                    "panel message $index"
                }
            db.saveConversationMessage("main", "user", content, null)
        }
    }

    private fun assertLatestPosition(
        placement: ConversationInputPlacement,
        listState: LazyListState,
    ) {
        assertFalse(
            listState.canScrollBackward,
            "latest position stopped at index=${listState.firstVisibleItemIndex}, " +
                "offset=${listState.firstVisibleItemScrollOffset}, " +
                "items=${listState.layoutInfo.totalItemsCount}",
        )
        assertEquals(0, listState.firstVisibleItemIndex, "latest item must be timeline index 0")
        assertEquals(0, listState.firstVisibleItemScrollOffset, "latest item must have no residual offset")
        if (placement == ConversationInputPlacement.CONVERSATION_MESSAGE) {
            composeTestRule.onNodeWithText("Type a message…").assertIsDisplayed()
        }
    }

    private fun scrollToLatestButtonExists(): Boolean =
        composeTestRule.onAllNodesWithContentDescription("Scroll to latest message").fetchSemanticsNodes().isNotEmpty()
}
