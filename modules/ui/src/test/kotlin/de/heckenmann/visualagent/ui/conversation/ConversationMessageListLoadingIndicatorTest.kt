@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Reproduces conversation waiting-indicator visibility defects from issue #200.
 */
class ConversationMessageListLoadingIndicatorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `waiting indicator is hidden when local sending flag is stale but no activity is in flight`() {
        val inFlight = InFlightStateHolder()

        renderMessageList(inFlight = inFlight, sending = true)

        composeTestRule.onNodeWithText("Thinking").assertDoesNotExist()
    }

    @Test
    fun `waiting indicator is shown for an active stream from canonical in-flight state`() {
        val inFlight = InFlightStateHolder().also { it.markStreamStart("request-1") }

        renderMessageList(inFlight = inFlight, sending = false)

        composeTestRule.onNodeWithText("Thinking").assertExists()
    }

    @Test
    fun `waiting timeline item is visible during a stream and removed after it ends`() {
        val inFlight = InFlightStateHolder()

        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn {
                    ConversationTimeline(
                        items =
                            buildConversationTimeline(
                                history = emptyList(),
                                pendingUserMessage = null,
                                streamingContent = "",
                                showWaitingIndicator = inFlight.state.value.totalActive > 0,
                                showOlderHistoryLoading = false,
                                includeInlineComposer = false,
                            ),
                        sending = false,
                        deletingMessageIds = emptySet(),
                        onDeleteMessage = {},
                        onStatusChange = {},
                        onEditMessage = {},
                        sendContent = {},
                        inlineComposer = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Thinking").assertDoesNotExist()

        composeTestRule.runOnIdle { inFlight.markStreamStart("request-1") }
        composeTestRule.onNodeWithText("Thinking").assertExists()

        composeTestRule.runOnIdle { inFlight.markStreamEnd("request-1") }
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.onNodeWithText("Thinking").assertDoesNotExist()
    }

    @Test
    fun `waiting indicator stays hidden after the conversation history reloads`() {
        val inFlight = InFlightStateHolder()
        inFlight.markStreamStart("request-1")
        inFlight.markStreamEnd("request-1")

        val reloadedTimeline =
            buildConversationTimeline(
                history = listOf(Message("assistant", "Reloaded history", id = "reload-1")),
                pendingUserMessage = null,
                streamingContent = "",
                showWaitingIndicator = inFlight.state.value.totalActive > 0,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
            )

        assertTrue(reloadedTimeline.none { it is ConversationTimelineItem.Waiting })
    }

    private fun renderMessageList(
        inFlight: InFlightStateHolder,
        sending: Boolean,
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn {
                    ConversationMessageList(
                        history = emptyList(),
                        sending = sending,
                        inFlight = inFlight,
                        pendingUserMessage = null,
                        streamingContent = "",
                        deletingMessageIds = emptySet(),
                        onDeleteMessage = {},
                        onStatusChange = {},
                        onEditMessage = {},
                        sendContent = {},
                    )
                }
            }
        }
    }
}
