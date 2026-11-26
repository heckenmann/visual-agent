package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import de.heckenmann.visualagent.ui.CompletionIdlingResource
import de.heckenmann.visualagent.ui.awaitCompletion
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        composeTestRule.awaitCompletion(onboarding.modelDiscovery) {
            composeTestRule.onNodeWithText("Continue").performClick()
        }
        composeTestRule.onNodeWithText("Discovered model").assertExists()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Review").assertExists()
        composeTestRule.awaitCompletion(onboarding.providerSaved) {
            composeTestRule.onNodeWithText("Save and continue").performClick()
        }
        composeTestRule.onNodeWithText("Existing agent").assertExists()
        composeTestRule.onNodeWithText(DEFAULT_RESEARCHER_REQUEST).assertExists()
        composeTestRule.awaitCompletion(onboarding.agentCreation) {
            composeTestRule.onNodeWithText("Ask model to create sub-agent").performClick()
        }
        composeTestRule
            .onNodeWithText("Created Researcher", substring = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                assertTrue(scrollBy(0f, 100f))
            }
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Terminal Access").assertExists()
        composeTestRule.awaitCompletion(onboarding.completion) {
            composeTestRule.onNodeWithText("Finish").performClick()
        }

        assertTrue(onboarding.finished)
        assertTrue(onboarding.completed)
    }

    @Test
    fun `failed validation preserves the provider draft and model for a successful retry`() {
        val onboarding = TestOnboardingPort(failFirstValidation = true)
        composeTestRule.setContent {
            MaterialTheme {
                ComposeOnboardingWizard(onboarding, automatic = false, onFinished = {})
            }
        }
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Add provider").performClick()
        composeTestRule.onNodeWithText("Provider name").performTextReplacement("Retry provider")
        composeTestRule.awaitCompletion(onboarding.modelDiscovery) {
            composeTestRule.onNodeWithText("Continue").performClick()
        }
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Save and continue").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            onboarding.validationRequests.isNotEmpty() &&
                composeTestRule.onAllNodesWithText("The provider is temporarily unavailable.").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("The provider is temporarily unavailable.").assertIsDisplayed()
        val firstAttempt = onboarding.validationRequests.single()
        assertEquals("Retry provider", firstAttempt.first.name)
        assertEquals("discovered", firstAttempt.second)
        assertTrue(onboarding.savedConfigurations.isEmpty())
        assertFalse(onboarding.completed)
        composeTestRule.onNodeWithText("Discovered model").assertIsSelected()

        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Review").assertIsDisplayed()
        composeTestRule.awaitCompletion(onboarding.providerSaved) {
            composeTestRule.onNodeWithText("Save and continue").performClick()
        }
        composeTestRule.onNodeWithText("Create your first sub-agent").assertIsDisplayed()
        assertEquals(listOf(firstAttempt, firstAttempt), onboarding.validationRequests.toList())
        assertEquals(Triple(firstAttempt.first, firstAttempt.second, "fingerprint"), onboarding.savedConfigurations.single())
    }

    @Test
    fun `model list scrolls independently at larger font scale`() {
        val models =
            List(40) { ProviderModel("model-$it") } +
                ProviderModel("long-model", "A model with a long display name that wraps across lines at larger font sizes")
        val selection = mutableStateOf<ProviderModel?>(null)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.5f)) {
                MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                    Column(Modifier.size(640.dp, 320.dp)) {
                        OnboardingModelStep(
                            draft = null,
                            models = models,
                            selectedModel = selection.value,
                            loading = false,
                            error = "The server could not validate this model. Select another model or retry.",
                            onSelect = { selection.value = it },
                            onRefresh = {},
                        )
                    }
                }
            }
        }
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(models.lastIndex)
        composeTestRule
            .onNodeWithText(models.last().name)
            .assertIsDisplayed()
            .performClick()
            .assertIsSelected()
        composeTestRule.onNodeWithText("Search models").performTextReplacement("no matching model")
        composeTestRule.onNodeWithText("No models match your search.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Selected model: ${models.last().name}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search models").performTextReplacement("LONG-MODEL")
        composeTestRule.onNodeWithText(models.last().name).assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithText("Refresh models").assertIsDisplayed()
        composeTestRule.onNodeWithText("The server could not validate", substring = true).assertIsDisplayed()
    }

    private class TestOnboardingPort(
        private val failFirstValidation: Boolean = false,
    ) : OnboardingPort {
        var finished = false
        var discoveryCalled = false
        var creationCalled = false
        var completed = false
        val modelDiscovery = CompletionIdlingResource("model discovery")
        val providerSaved = CompletionIdlingResource("provider save")
        val agentCreation = CompletionIdlingResource("agent creation")
        val completion = CompletionIdlingResource("onboarding completion")
        val validationRequests = CopyOnWriteArrayList<Pair<OnboardingProviderDraft, String>>()
        val savedConfigurations = CopyOnWriteArrayList<Triple<OnboardingProviderDraft, String, String>>()

        override fun state() = OnboardingState(OnboardingStatus.NOT_STARTED, version = 1)

        override fun providers() = emptyList<OnboardingProviderProfile>()

        override fun agents() = listOf(OnboardingAgent("existing", "Existing agent", "Existing role"))

        override fun dismiss() = Unit

        override suspend fun discoverModels(draft: OnboardingProviderDraft): List<ProviderModel> {
            discoveryCalled = true
            modelDiscovery.complete()
            return listOf(ProviderModel(id = "discovered", name = "Discovered model"))
        }

        override suspend fun validate(
            draft: OnboardingProviderDraft,
            modelId: String,
        ): OnboardingValidationResult {
            validationRequests += draft to modelId
            return if (failFirstValidation && validationRequests.size == 1) {
                OnboardingValidationResult(false, OnboardingValidationCode.ENDPOINT_UNAVAILABLE, "The provider is temporarily unavailable.")
            } else {
                OnboardingValidationResult(true, OnboardingValidationCode.SUCCESS, "Ready", "fingerprint")
            }
        }

        override suspend fun finish(
            draft: OnboardingProviderDraft,
            model: ProviderModel,
            validationFingerprint: String,
        ) {
            savedConfigurations += Triple(draft, model.id, validationFingerprint)
            finished = true
            providerSaved.complete()
        }

        override suspend fun createAgent(description: String): OnboardingAgentCreationResult {
            creationCalled = true
            agentCreation.complete()
            return OnboardingAgentCreationResult(
                "Created Researcher\n" + "Researcher setup details.\n".repeat(100),
                agents() + OnboardingAgent("researcher", "Researcher", "Research topics"),
            )
        }

        override fun complete() {
            completed = true
            completion.complete()
        }
    }
}
