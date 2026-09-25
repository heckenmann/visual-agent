package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.heckenmann.visualagent.protocol.MainAgentMemoryPort
import de.heckenmann.visualagent.protocol.MainAgentMemorySnapshot
import de.heckenmann.visualagent.protocol.ModelDetails
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/** Verifies model-specific context limits in the provider settings overlay. */
class ComposeProviderSettingsContextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `selecting a model adopts its reported context limit in either direction`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        val providers = mockk<ProviderPort>(relaxed = true)
        val memory = mockk<MainAgentMemoryPort>()
        val profile =
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "small-model",
                models =
                    listOf(
                        ProviderModel("small-model", contextLimit = 8192),
                        ProviderModel("large-model"),
                        ProviderModel("medium-model", contextLimit = 16384),
                    ),
            )
        val current = SettingsSnapshot(providerId = "ollama", modelId = "small-model", contextLength = 4096)
        coEvery { settings.snapshotAsync() } returns current
        every { settings.snapshot() } returns current
        every { providers.listProviders() } returns listOf(profile)
        every { memory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12_000, 0)
        coEvery { providers.modelDetails("ollama", "large-model") } returns
            ModelDetails(model = "large-model", modifiedAt = "now", contextLimit = 65536)

        composeTestRule.setContent {
            MaterialTheme { providerSettingsOverlay(settings, memory, providers, onSettingsChanged = {}) }
        }
        composeTestRule.waitForIdle()
        contextLength().assertTextContains("4096", substring = true)

        composeTestRule.onNodeWithText("small-model").performScrollTo().performClick()
        composeTestRule.onNodeWithText("large-model").performClick()
        composeTestRule.waitForIdle()
        contextLength().assertTextContains("65536", substring = true)

        composeTestRule.onNodeWithText("large-model").performClick()
        composeTestRule.onNodeWithText("medium-model").performClick()
        composeTestRule.waitForIdle()
        contextLength().assertTextContains("16384", substring = true)

        composeTestRule.onNodeWithText("Save changes").performClick()
        composeTestRule.waitForIdle()
        verify {
            settings.save(
                match { it.contextLength == 16384 },
                match { it.modelId == "medium-model" },
            )
        }
    }

    private fun contextLength() = composeTestRule.onNodeWithContentDescription("Context length")
}
