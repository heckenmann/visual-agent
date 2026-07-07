@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

/**
 * Compose tests for the header in-flight activity indicator.
 */
class ActivityIndicatorComposeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `indicator is hidden when no activity is in flight`() {
        composeTestRule.setContent {
            MaterialTheme {
                InFlightIndicator(state = InFlightState())
            }
        }
        composeTestRule.onNodeWithContentDescription("Agent busy: Idle").assertDoesNotExist()
    }

    @Test
    fun `indicator shows description when activity is in flight`() {
        composeTestRule.setContent {
            MaterialTheme {
                InFlightIndicator(
                    state =
                        InFlightState(
                            streamingRequestIds = setOf("req-1"),
                            runningAgentIds = setOf("agent-1"),
                        ),
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule
            .onNodeWithContentDescription("Agent busy: 1 chat stream, 1 sub-agent")
            .assertExists()
    }
}
