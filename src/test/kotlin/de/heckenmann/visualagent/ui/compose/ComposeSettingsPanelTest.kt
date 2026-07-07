@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class ComposeSettingsPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `settings panel renders provider and execution sections`() {
        val catalog = mockk<ProviderCatalogService>()
        every { catalog.enabledProviders() } returns
            listOf(
                ProviderProfile(
                    id = "ollama",
                    name = "Ollama",
                    adapter = de.heckenmann.visualagent.agent.provider.ProviderAdapter.OLLAMA,
                    baseUrl = "http://localhost:11434",
                    defaultModel = "llava",
                ),
            )
        every { catalog.activeProviderId() } returns "ollama"
        every { catalog.getProvider("ollama") } returns catalog.enabledProviders().single()
        every { catalog.selectableModels(any()) } returns emptyList()
        val llmProvider = mockk<LLMProvider>(relaxed = true)
        val inFlight = InFlightStateHolder()

        composeTestRule.setContent {
            MaterialTheme {
                SettingsPanel(
                    config = AppConfig.instance,
                    llmProvider = llmProvider,
                    providerCatalogService = catalog,
                    modalRequester = ComposeModalRequester { },
                    onSettingsChanged = {},
                    inFlight = inFlight,
                )
            }
        }

        composeTestRule.onNodeWithText("Provider and model").assertExists()
        composeTestRule.onNodeWithText("Execution").assertExists()
        composeTestRule.onNodeWithText("Appearance").assertExists()
        composeTestRule.onNodeWithText("Base URL").assertExists()
    }
}
