package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ModelDetails
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies provider and runtime settings through transport-neutral ports. */
class ComposeProtocolSettingsPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel renders provider and execution settings`() {
        var detailsLoaded = false
        val settings = mockk<SettingsPort>(relaxed = true)
        every { settings.snapshot() } returns SettingsSnapshot()
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot()
        every { settings.addChangeListener(any()) } returns AutoCloseable { }
        val providers = mockk<ProviderPort>(relaxed = true)
        every { providers.listProviders() } returns listOf(ProviderProfile("ollama", "Ollama", ProviderAdapter.OLLAMA, ""))
        every { providers.enabledProviders() } returns listOf(ProviderProfile("ollama", "Ollama", ProviderAdapter.OLLAMA, ""))
        every { providers.activeProviderId() } returns "ollama"
        every { providers.activeModelId() } returns "llama"
        every { providers.selectableModels("ollama") } returns listOf(ProviderModel("llama", "Llama"))
        coEvery { providers.modelDetails("ollama", "llama") } answers {
            detailsLoaded = true
            ModelDetails("llama", "today")
        }
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

        composeTestRule.onNodeWithText("Provider connections").assertExists()
        composeTestRule.onNodeWithText("Execution").assertExists()
        composeTestRule.onNodeWithText("Appearance").assertExists()
        composeTestRule.onNodeWithText("Context length").assertExists()
        composeTestRule.onNodeWithContentDescription("Save provider and model").assertExists()
        composeTestRule.onNodeWithContentDescription("Save settings").assertExists().assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Refresh models").assertExists()
        composeTestRule.onNodeWithContentDescription("Refresh model details").assertDoesNotExist()
        composeTestRule.waitUntil(5_000) { detailsLoaded }
    }

    @Test
    fun `save action persists the current settings snapshot`() {
        var settingsSaved = false
        var selectionSaved = false
        val settings = mockk<SettingsPort>(relaxed = true)
        every { settings.snapshot() } returns SettingsSnapshot()
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot()
        every { settings.addChangeListener(any()) } returns AutoCloseable { }
        every { settings.save(any()) } answers { settingsSaved = true }
        val providers = mockk<ProviderPort>(relaxed = true)
        every { providers.listProviders() } returns listOf(ProviderProfile("ollama", "Ollama", ProviderAdapter.OLLAMA, ""))
        every { providers.enabledProviders() } returns listOf(ProviderProfile("ollama", "Ollama", ProviderAdapter.OLLAMA, ""))
        every { providers.activeProviderId() } returns "ollama"
        every { providers.activeModelId() } returns "llama"
        every { providers.selectableModels(any()) } returns listOf(ProviderModel("llama", "Llama"))
        every { providers.addChangeListener(any()) } returns AutoCloseable { }
        every { providers.setActiveSelection(any(), any()) } answers { selectionSaved = true }

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

        composeTestRule
            .onNodeWithContentDescription(
                "Save settings",
            ).performScrollTo()
            .assert(hasClickAction())
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitUntil(5_000) { settingsSaved && selectionSaved }
        assertTrue(settingsSaved)
        assertTrue(selectionSaved)
    }

    @Test
    fun `settings save is disabled when provider has no selectable model`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        every { settings.snapshot() } returns SettingsSnapshot(modelId = "")
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(modelId = "")
        every { settings.addChangeListener(any()) } returns AutoCloseable { }
        val providers = mockk<ProviderPort>(relaxed = true)
        every { providers.listProviders() } returns listOf(ProviderProfile("ollama", "Ollama", ProviderAdapter.OLLAMA, ""))
        every { providers.activeProviderId() } returns "ollama"
        every { providers.activeModelId() } returns ""
        every { providers.selectableModels("ollama") } returns emptyList()
        every { providers.addChangeListener(any()) } returns AutoCloseable { }

        composeTestRule.setContent {
            MaterialTheme {
                SettingsPanel(settings, providers, ComposeModalRequester { }, {}, InFlightStateHolder(), mockk(relaxed = true))
            }
        }

        composeTestRule.onNodeWithContentDescription("Save settings").assertIsNotEnabled()
    }
}
