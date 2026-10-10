package de.heckenmann.visualagent.agent.provider

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Verifies that discovery preserves the configured Codex default and active selection. */
class ProviderCatalogCodexDiscoveryTest {
    @Test
    fun `model id discovery retains the configured Codex default`() {
        verifyDiscovery { catalog, discovered ->
            catalog.updateDiscoveredModels("codex-custom", discovered)
        }
    }

    @Test
    fun `model config discovery retains the configured Codex default`() {
        verifyDiscovery { catalog, discovered ->
            catalog.updateDiscoveredModelConfigs("codex-custom", discovered.map { ProviderModelConfig(it) })
        }
    }

    private fun verifyDiscovery(discover: (ProviderCatalogService, List<String>) -> Unit) {
        for (discovered in listOf(emptyList(), listOf("another-model"))) {
            val catalog = ProviderCatalogService(MemoryPreferences())
            catalog.saveProvider(
                ProviderProfile(
                    id = "codex-custom",
                    name = "Codex",
                    adapter = ProviderAdapter.CODEX_CLI,
                    baseUrl = "",
                    defaultModel = "configured-model",
                    options = mapOf("temperature" to "0.3"),
                ),
            )
            catalog.setActiveSelection("codex-custom", "configured-model")

            discover(catalog, discovered)

            val profile = requireNotNull(catalog.getProvider("codex-custom"))
            assertEquals("configured-model", profile.defaultModel)
            assertEquals(mapOf("temperature" to "0.3"), profile.options)
            assertEquals(discovered + "configured-model", catalog.selectableModels(profile.id).map { it.id })
            assertEquals("codex-custom", catalog.activeProviderId())
            assertEquals("configured-model", catalog.activeModelId())
            // The unchanged settings must still pass complete-catalog selection validation.
            catalog.replaceConfiguration(
                ProviderConfiguration(catalog.listProviders(), catalog.activeProviderId(), catalog.activeModelId()),
            )
        }
    }

    private class MemoryPreferences : ProviderPreferenceStore {
        private val values = mutableMapOf<String, String>()

        override fun getPreference(key: String): String? = values[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }
}
