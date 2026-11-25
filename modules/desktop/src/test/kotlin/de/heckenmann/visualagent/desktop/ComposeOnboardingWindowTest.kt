package de.heckenmann.visualagent.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.OnboardingPort
import de.heckenmann.visualagent.protocol.OnboardingProviderProfile
import de.heckenmann.visualagent.protocol.OnboardingState
import de.heckenmann.visualagent.protocol.OnboardingStatus
import de.heckenmann.visualagent.protocol.ProviderAdapter
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/** Verifies both manual setup exit paths at the desktop window boundary. */
class ComposeOnboardingWindowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Escape discards an unsaved manual provider draft`() =
        verifyManualCancellation {
            compose.onNodeWithText("Unsaved provider").performKeyInput {
                keyDown(Key.Escape)
                keyUp(Key.Escape)
            }
        }

    @Test
    fun `window close discards an unsaved manual provider draft`() =
        verifyManualCancellation { closeWindow ->
            compose.runOnIdle { closeWindow() }
        }

    private fun verifyManualCancellation(cancel: (() -> Unit) -> Unit) {
        val existing =
            OnboardingProviderProfile(
                "saved",
                "Saved provider",
                ProviderAdapter.OLLAMA,
                "http://localhost:11434",
                credentialConfigured = false,
                enabled = true,
                defaultModel = "",
                models = emptyList(),
            )
        val onboarding = mockk<OnboardingPort>()
        every { onboarding.state() } returns OnboardingState(OnboardingStatus.COMPLETED, 1)
        every { onboarding.providers() } returns listOf(existing)
        val application = mockk<ApplicationPort>(relaxed = true)
        every { application.onboarding } returns onboarding
        val open = mutableStateOf(true)
        lateinit var closeWindow: () -> Unit
        compose.setContent {
            if (open.value) {
                ComposeOnboardingWindow(
                    application,
                    automatic = false,
                    onFinished = { open.value = false },
                    onCloseRequest = { open.value = false },
                    windowHost = { _, onCloseRequest, content ->
                        SideEffect { closeWindow = onCloseRequest }
                        content()
                    },
                )
            }
        }

        openProviderStep()
        compose.onNodeWithText("Add provider").performClick()
        val name = compose.onNodeWithText("Provider name")
        name.performClick()
        name.performTextReplacement("Unsaved provider")
        compose.onNodeWithText("Unsaved provider").assertIsDisplayed()

        cancel(closeWindow)
        compose.onNodeWithText("Set up Visual Agent").assertDoesNotExist()
        compose.runOnIdle { open.value = true }
        openProviderStep()
        compose.onNodeWithText("Unsaved provider").assertDoesNotExist()
        compose.onNodeWithText("New provider").assertDoesNotExist()
        verify(exactly = 0) { onboarding.dismiss() }
        verify(exactly = 0) { onboarding.complete() }
        coVerify(exactly = 0) { onboarding.finish(any(), any(), any()) }
    }

    private fun openProviderStep() {
        compose.onNodeWithText("Continue").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Saved provider").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Saved provider").assertIsDisplayed()
    }
}
