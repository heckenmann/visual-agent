@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class ComposeSettingsPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `settings panel selects the provider once and lists its models`() {
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
        every { catalog.listProviders() } returns catalog.enabledProviders()
        every { catalog.activeProviderId() } returns "ollama"
        every { catalog.activeModelId() } returns "llava"
        every { catalog.getProvider("ollama") } returns catalog.enabledProviders().single()
        every { catalog.selectableModels(any()) } returns
            listOf(
                de.heckenmann.visualagent.agent.provider.ProviderModelConfig(
                    id = "llava",
                    name = "LLaVA",
                ),
            )
        val llmProvider = mockk<LLMProvider>(relaxed = true)
        val inFlight = InFlightStateHolder()

        composeTestRule.setContent {
            MaterialTheme {
                SettingsPanel(
                    config = AppConfigBean(),
                    llmProvider = llmProvider,
                    providerCatalogService = catalog,
                    modalRequester = ComposeModalRequester { },
                    onSettingsChanged = {},
                    inFlight = inFlight,
                    toolEventBus = ToolEventBus(),
                )
            }
        }

        composeTestRule.onNodeWithText("Provider connections").assertExists()
        composeTestRule.onNodeWithText("Main agent model").assertExists()
        composeTestRule.onNodeWithText("Choose the connection the main agent should use.").assertExists()
        composeTestRule.onNodeWithText("LLaVA (llava)").assertExists()
        composeTestRule.onNodeWithContentDescription("Add LLaVA (llava) to favorites").assertExists()
        composeTestRule.onNodeWithText("Main agent selection").assertDoesNotExist()
        composeTestRule.onNodeWithText("Custom model ID").assertDoesNotExist()
        composeTestRule.onNodeWithText("Execution").assertExists()
        composeTestRule.onNodeWithText("Appearance").assertExists()
        composeTestRule.onNodeWithText("Base URL").assertDoesNotExist()
    }
}
