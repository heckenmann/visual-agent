package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import de.heckenmann.visualagent.protocol.ModelDetails
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.settings.forms.ConversationSettingsPage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

/** Regression coverage for delayed provider replies and recoverable catalog failures. */
class ConversationSettingsRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val providers = mockk<ProviderPort>(relaxed = true)
    private val settings = mockk<SettingsPort>(relaxed = true)
    private val profile =
        ProviderProfile("local", "Local provider", ProviderAdapter.OLLAMA, "http://localhost", models = listOf(ProviderModel("model")))

    @Test fun `a delayed context capacity preserves a newer smaller user budget`() {
        val requested = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<ModelDetails>()
        every { providers.listProviders() } returns listOf(profile)
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(providerId = "local", modelId = "model", contextLength = 65536)
        coEvery { providers.modelDetails("local", "model") } coAnswers {
            requested.complete(Unit)
            reply.await()
        }
        compose.setContent { MaterialTheme { ConversationSettingsPage(settings, providers, {}, {}) } }
        compose.waitUntil { requested.isCompleted }
        compose.onNodeWithContentDescription("Context length").performTextReplacement("8192")
        compose.runOnIdle { reply.complete(ModelDetails("model", "today", contextLimit = 32768)) }
        compose.waitUntil { runCatching { compose.onNodeWithText("Model limit: 32768 tokens").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("Context length").assertTextContains("8192")
    }

    @Test fun `provider catalog failure exposes a retry and retains the open settings dialog`() {
        every { providers.listProviders() } throws IllegalStateException("Synthetic unavailable catalog") andThen listOf(profile)
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(providerId = "local", modelId = "model")
        coEvery { providers.modelDetails(any(), any()) } returns ModelDetails("model", "today")
        compose.setContent { MaterialTheme { ConversationSettingsPage(settings, providers, {}, {}) } }
        compose.waitUntil { runCatching { compose.onNodeWithContentDescription("Retry loading providers").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("Context length").performTextReplacement("8192")
        compose.onNodeWithContentDescription("Retry loading providers").performClick()
        compose.waitUntil { runCatching { compose.onNodeWithText("Local provider").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("Context length").assertTextContains("8192")
        verify(exactly = 2) { providers.listProviders() }
    }
}
