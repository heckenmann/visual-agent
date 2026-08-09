@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    @Test
    fun `refresh models persists discovered models before updating the selection`() {
        val profile =
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "llava",
            )
        val catalog = mockk<ProviderCatalogService>(relaxed = true)
        every { catalog.enabledProviders() } returns listOf(profile)
        every { catalog.listProviders() } returns listOf(profile)
        every { catalog.activeProviderId() } returns profile.id
        every { catalog.activeModelId() } returns profile.defaultModel
        every { catalog.getProvider(profile.id) } returns profile
        every { catalog.selectableModels(profile.id) } returns listOf()
        val llmProvider = mockk<LLMProvider>(relaxed = true)
        coEvery { llmProvider.getModels(profile.id) } returns listOf("llava", "qwen3")

        composeTestRule.setContent {
            MaterialTheme {
                SettingsPanel(
                    config = AppConfigBean(),
                    llmProvider = llmProvider,
                    providerCatalogService = catalog,
                    modalRequester = ComposeModalRequester { },
                    onSettingsChanged = {},
                    inFlight = InFlightStateHolder(),
                    toolEventBus = ToolEventBus(),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Refresh models").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                verify { catalog.updateDiscoveredModels(profile.id, listOf("llava", "qwen3")) }
            }.isSuccess
        }
    }
}
