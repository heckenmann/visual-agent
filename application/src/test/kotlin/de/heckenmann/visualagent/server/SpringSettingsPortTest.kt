package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.config.ThemeMode
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderConfiguration
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import de.heckenmann.visualagent.protocol.ThemeMode as ProtocolThemeMode

/** Verifies that settings exposed to the UI are read from and written to the server bean. */
class SpringSettingsPortTest {
    @Test
    fun `snapshot reflects persisted server configuration`() {
        val config = AppConfigBean()
        config.llmProvider = "openai"
        config.openAiModel = "gpt-test"
        config.uiThemeMode = ThemeMode.DARK
        config.uiScalePercent = 125
        config.queueFlushMode = "ALL"
        val catalog = mockk<ProviderCatalogService>()
        every { catalog.activeProviderId() } returns "openai"
        every { catalog.activeModelId() } returns "gpt-test"

        val snapshot = SpringSettingsPort(config, catalog).snapshot()

        assertEquals("openai", snapshot.providerId)
        assertEquals("gpt-test", snapshot.modelId)
        assertEquals(ProtocolThemeMode.DARK, snapshot.uiThemeMode)
        assertEquals(125, snapshot.uiScalePercent)
        assertEquals("ALL", snapshot.queueFlushMode)
    }

    @Test
    fun `save writes the complete settings snapshot to the server bean`() {
        val config = AppConfigBean()
        val catalog = mockk<ProviderCatalogService>(relaxed = true)
        val port = SpringSettingsPort(config, catalog)

        port.save(
            SettingsSnapshot(
                providerId = "openai",
                modelId = "gpt-test",
                uiThemeMode = ProtocolThemeMode.LIGHT,
                uiScalePercent = 250,
                queueFlushMode = "ALL",
            ),
        )

        verify(exactly = 0) { catalog.setActiveSelection(any(), any()) }
        assertEquals(ThemeMode.LIGHT, config.uiThemeMode)
        assertEquals(200, config.uiScalePercent)
        assertEquals("ALL", config.queueFlushMode)
    }

    @Test
    fun `save persists a staged provider configuration with its settings draft`() {
        val config = AppConfigBean()
        val catalog = mockk<ProviderCatalogService>(relaxed = true)
        val port = SpringSettingsPort(config, catalog)
        val provider =
            ProviderProfile(
                id = "openai",
                name = "OpenAI",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://api.openai.com",
                models = listOf(ProviderModel("gpt-test")),
            )

        port.save(
            SettingsSnapshot(providerId = "openai", modelId = "gpt-test", favoriteModels = listOf("gpt-test")),
            ProviderConfiguration(listOf(provider), "openai", "gpt-test"),
        )

        verify {
            catalog.replaceConfiguration(
                match { configuration ->
                    configuration.providerId == "openai" &&
                        configuration.modelId == "gpt-test" &&
                        configuration.providers.single().id == "openai"
                },
            )
        }
        assertEquals("gpt-test", config.favoriteModels)
    }
}
