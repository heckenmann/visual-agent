package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.protocol.MainAgentMemoryPort
import de.heckenmann.visualagent.protocol.MainAgentMemorySnapshot
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.settings.providers.ProviderManagementPanel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/** Verifies the settings overlay exposes the complete provider and conversation controls. */
class ComposeProviderSettingsControlsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `settings overlay renders provider and conversation controls without persisting`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        val provider =
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "llama",
                models = listOf(ProviderModel("llama")),
            )
        val providers = mockk<ProviderPort>(relaxed = true)
        val memory = mockk<MainAgentMemoryPort>()
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(providerId = "ollama", modelId = "llama")
        every { settings.snapshot() } returns SettingsSnapshot(providerId = "ollama", modelId = "llama")
        every { providers.listProviders() } returns listOf(provider)
        every { memory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12_000, 0)

        composeTestRule.setContent {
            MaterialTheme { ProviderManagementPanel(settings, providers, onSaved = {}) }
        }

        composeTestRule.onNodeWithText("Provider settings loaded").assertExists()
        composeTestRule.onNodeWithText("Conversation").assertDoesNotExist()
        composeTestRule.onNodeWithText("Model instruction").assertDoesNotExist()
        composeTestRule.onNodeWithText("Queue flush").assertDoesNotExist()
        composeTestRule.onNodeWithText("Main-agent memory").assertDoesNotExist()
        listOf(
            "Model instruction",
            "Context length",
            "Startup history",
            "Parallel agents",
            "Default tool timeout (seconds)",
            "Queue flush",
        ).forEach { label ->
            composeTestRule.onNodeWithContentDescription("$label information").assertDoesNotExist()
        }
        composeTestRule.onNodeWithText("Model catalog").assertExists()
        listOf("Add provider", "Edit provider", "Remove provider", "Refresh models").forEach { label ->
            composeTestRule.onNodeWithContentDescription(label).assertExists()
        }
        composeTestRule.onNodeWithContentDescription("Run onboarding again").assertExists()
        verify(exactly = 0) { settings.save(any(), any()) }
    }
}
