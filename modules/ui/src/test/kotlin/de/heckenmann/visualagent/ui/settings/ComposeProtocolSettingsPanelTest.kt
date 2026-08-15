package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/** Verifies provider and runtime settings through transport-neutral ports. */
class ComposeProtocolSettingsPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel renders provider and execution settings`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        every { settings.snapshot() } returns SettingsSnapshot()
        every { settings.addChangeListener(any()) } returns AutoCloseable { }
        val providers = mockk<ProviderPort>(relaxed = true)
        every { providers.enabledProviders() } returns listOf(ProviderProfile("ollama", "Ollama", ProviderAdapter.OLLAMA, ""))
        every { providers.activeProviderId() } returns "ollama"
        every { providers.activeModelId() } returns "llama"
        every { providers.selectableModels("ollama") } returns listOf(ProviderModel("llama", "Llama"))
        every { providers.addChangeListener(any()) } returns AutoCloseable { }

        composeTestRule.setContent {
            MaterialTheme {
                SettingsPanel(
                    settings,
                    providers,
                    ComposeModalRequester { },
                    {},
                    InFlightStateHolder(),
                    mockk<ActivityPort>(relaxed = true),
                )
            }
        }

        composeTestRule.onNodeWithText("Provider and model").assertExists()
        composeTestRule.onNodeWithText("Execution and appearance").assertExists()
        composeTestRule.onNodeWithText("Context length").assertExists()
        composeTestRule.onNodeWithContentDescription("Save provider and model").assertExists()
        composeTestRule.onNodeWithContentDescription("Save settings").assertExists()
    }

    @Test
    fun `save action persists the current settings snapshot`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        every { settings.snapshot() } returns SettingsSnapshot()
        every { settings.addChangeListener(any()) } returns AutoCloseable { }
        val providers = mockk<ProviderPort>(relaxed = true)
        every { providers.enabledProviders() } returns listOf(ProviderProfile("ollama", "Ollama", ProviderAdapter.OLLAMA, ""))
        every { providers.activeProviderId() } returns "ollama"
        every { providers.activeModelId() } returns "llama"
        every { providers.selectableModels(any()) } returns listOf(ProviderModel("llama", "Llama"))
        every { providers.addChangeListener(any()) } returns AutoCloseable { }

        composeTestRule.setContent {
            MaterialTheme {
                SettingsPanel(
                    settings,
                    providers,
                    ComposeModalRequester { },
                    {},
                    InFlightStateHolder(),
                    mockk<ActivityPort>(relaxed = true),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Save settings").performClick()
        verify { settings.save(any()) }
        verify { providers.setActiveSelection("ollama", "llama") }
    }
}
