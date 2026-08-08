@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

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
    fun `fixed input overlay is visible during a stream and removed after it ends`() {
        val inFlight = InFlightStateHolder()

        composeTestRule.setContent {
            MaterialTheme {
                Box {
                    ConversationWaitingOverlay(
                        visible = inFlight.state.value.totalActive > 0,
                        bottomPadding = 64.dp,
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
