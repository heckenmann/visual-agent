@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.status

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
