package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.OnboardingAgent
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationResult
import de.heckenmann.visualagent.protocol.OnboardingPort
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingProviderProfile
import de.heckenmann.visualagent.protocol.OnboardingState
import de.heckenmann.visualagent.protocol.OnboardingStatus
import de.heckenmann.visualagent.protocol.OnboardingValidationCode
import de.heckenmann.visualagent.protocol.OnboardingValidationResult
import de.heckenmann.visualagent.protocol.ProviderModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies that the onboarding wizard keeps navigation usable through the complete setup flow. */
class ComposeOnboardingWizardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `wizard completes setup with a selectable provider and model`() {
        var finished = false
        val onboarding = TestOnboardingPort()

        composeTestRule.setContent {
            MaterialTheme {
                ComposeOnboardingWizard(
                    onboarding = onboarding,
                    automatic = false,
                    onFinished = { finished = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Welcome").assertExists()
        composeTestRule.onNodeWithText("Main Agent — understands your request", substring = true).assertExists()
        composeTestRule.onNodeWithText("Sub-agents — specialized helpers", substring = true).assertExists()
        composeTestRule.onNodeWithText("Todos — durable work items", substring = true).assertExists()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("LLM provider").assertExists()
        composeTestRule.onNodeWithText("Add provider").performClick()
        composeTestRule.onNodeWithText("New provider").assertExists()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.waitUntil(5_000) { onboarding.discoveryCalled }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Discovered model", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("✓ Discovered model").assertExists()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Review").assertExists()
        composeTestRule.onNodeWithText("Save and continue").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Create your first sub-agent").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Existing agent").assertExists()
        composeTestRule.onNodeWithText(DEFAULT_RESEARCHER_REQUEST).assertExists()
        composeTestRule.onNodeWithText("Ask model to create sub-agent").performClick()
        composeTestRule.waitUntil(5_000) { onboarding.creationCalled }
        composeTestRule.onNodeWithText("Created Researcher").assertExists()
        composeTestRule.onNodeWithText("Finish").performClick()
        composeTestRule.waitUntil(5_000) { finished }

        assertTrue(onboarding.finished)
        assertTrue(onboarding.completed)
    }

    private class TestOnboardingPort : OnboardingPort {
        var finished = false
        var discoveryCalled = false
        var creationCalled = false
        var completed = false

        override fun state() = OnboardingState(OnboardingStatus.NOT_STARTED, version = 1)

        override fun providers() = emptyList<OnboardingProviderProfile>()

        override fun agents() = listOf(OnboardingAgent("existing", "Existing agent", "Existing role"))

        override fun dismiss() = Unit

        override suspend fun discoverModels(draft: OnboardingProviderDraft): List<ProviderModel> {
            discoveryCalled = true
            return listOf(ProviderModel(id = "discovered", name = "Discovered model"))
        }

        override suspend fun validate(
            draft: OnboardingProviderDraft,
            modelId: String,
        ) = OnboardingValidationResult(true, OnboardingValidationCode.SUCCESS, "Ready", "fingerprint")

        override suspend fun finish(
            draft: OnboardingProviderDraft,
            model: ProviderModel,
            validationFingerprint: String,
        ) {
            finished = true
        }

        override suspend fun createAgent(description: String): OnboardingAgentCreationResult {
            creationCalled = true
            return OnboardingAgentCreationResult(
                "Created Researcher",
                agents() + OnboardingAgent("researcher", "Researcher", "Research topics"),
            )
        }

        override fun complete() {
            completed = true
        }
    }
}
