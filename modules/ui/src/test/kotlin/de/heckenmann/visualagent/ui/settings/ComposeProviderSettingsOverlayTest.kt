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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/** Verifies that provider and model edits stay in the global overlay until explicitly saved. */
class ComposeProviderSettingsOverlayTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `adding a provider stages the editor without persisting`() {
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
            MaterialTheme {
                providerSettingsOverlay(settings, memory, providers, onSettingsChanged = {})
            }
        }

        composeTestRule.onNodeWithText("Main agent connection").assertExists()
        composeTestRule.onNodeWithText("Provider settings loaded").assertExists()
        composeTestRule.onNodeWithText("Conversation").assertExists()
        composeTestRule.onNodeWithText("Model instruction").assertExists()
        composeTestRule.onNodeWithText("Queue flush").assertExists()
        composeTestRule.onNodeWithText("Main-agent memory").assertExists()
        composeTestRule.onNodeWithText("Revision 0 · 0 / 12000 characters").assertExists()
        listOf(
            "Model instruction",
            "Context length",
            "Startup history",
            "Parallel agents",
            "Default tool timeout (seconds)",
            "Queue flush",
        ).forEach { label ->
            composeTestRule.onNodeWithContentDescription("$label information").assertExists()
        }
        listOf("Provider", "Model", "Favorite").forEach { label ->
            composeTestRule.onNodeWithContentDescription("$label information").assertExists()
        }
        listOf("Add provider", "Edit provider", "Remove provider", "Refresh models").forEach { label ->
            composeTestRule.onNodeWithText(label).assertExists()
        }
        verify(exactly = 0) { settings.save(any(), any()) }
    }
}
