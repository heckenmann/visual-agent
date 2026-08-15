package de.heckenmann.visualagent.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.ui.application.StartupStatus
import org.junit.Rule
import org.junit.Test

/** Verifies that startup failures and progress states are visible in the desktop splash. */
class ComposeStartupSplashTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `progress state shows application name and status`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStartupSplash(status = StartupStatus.startingServer(), onRetry = {})
            }
        }

        composeTestRule.onNodeWithText("Visual Agent").assertExists()
        composeTestRule.onNodeWithContentDescription("Visual Agent").assertExists()
        composeTestRule.onNodeWithText("Starting the local server").assertExists()
    }

    @Test
    fun `failure state shows retry action`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStartupSplash(status = StartupStatus.failed("Remote server unavailable"), onRetry = {})
            }
        }

        composeTestRule.onNodeWithText("Remote server unavailable").assertExists()
        composeTestRule.onNodeWithText("Retry").assertExists()
    }
}
