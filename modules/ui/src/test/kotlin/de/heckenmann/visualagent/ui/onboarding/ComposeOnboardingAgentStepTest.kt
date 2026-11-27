package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationAvailability
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies availability and creation feedback in the optional sub-agent step. */
class ComposeOnboardingAgentStepTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `unavailable model explains tool calling and offers model selection`() {
        var selectedAnotherModel = false
        val availability =
            OnboardingAgentCreationAvailability(
                available = false,
                reason = "The selected model cannot call tools. Choose a model with tool calling enabled, or skip this step.",
                canSelectAnotherModel = true,
            )
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    OnboardingAgentStep(
                        agents = emptyList(),
                        description = DEFAULT_RESEARCHER_REQUEST,
                        creating = false,
                        resultMessage = null,
                        error = null,
                        availability = availability,
                        onDescriptionChange = {},
                        onCreate = {},
                        onSelectModel = { selectedAnotherModel = true },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(availability.reason!!).assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose another model").performClick()

        assertTrue(selectedAnotherModel)
    }

    @Test
    fun `model response without creation is shown as an error`() {
        val error = "The model replied without creating a sub-agent. Revise the request or skip this step."
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    OnboardingAgentStep(
                        agents = emptyList(),
                        description = DEFAULT_RESEARCHER_REQUEST,
                        creating = false,
                        resultMessage = "Here are some suggestions.",
                        error = error,
                        availability = OnboardingAgentCreationAvailability(available = true),
                        onDescriptionChange = {},
                        onCreate = {},
                        onSelectModel = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(error).assertIsDisplayed()
    }
}
