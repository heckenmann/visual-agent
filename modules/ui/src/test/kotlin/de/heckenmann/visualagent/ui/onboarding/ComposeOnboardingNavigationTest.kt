package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies navigation for the optional onboarding sub-agent step. */
class ComposeOnboardingNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `agent setup can be skipped independently`() {
        var skipped = false
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingNavigation(
                    step = 4,
                    automatic = true,
                    busy = false,
                    canContinue = true,
                    canSelectModel = true,
                    onBack = {},
                    onCancel = {},
                    onContinue = {},
                    onSkipAgents = { skipped = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Skip agent setup").performClick()

        assertTrue(skipped)
    }
}
