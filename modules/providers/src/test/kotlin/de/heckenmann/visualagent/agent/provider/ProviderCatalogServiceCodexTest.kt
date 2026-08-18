package de.heckenmann.visualagent.agent.provider

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Verifies Codex-specific provider catalog behavior. */
class ProviderCatalogServiceCodexTest {
    @Test
    fun `catalog seeds the codex profile without selecting it`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        val profile = requireNotNull(catalog.getProvider(ProviderEnvironmentCredentials.CODEX_PROFILE_ID))

        assertEquals("Codex CLI", profile.name)
        assertEquals(ProviderAdapter.CODEX_CLI, profile.adapter)
        assertEquals("", profile.baseUrl)
        assertEquals("ollama", catalog.activeProviderId())
    }

    @Test
    fun `codex default model is selectable without model discovery`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        catalog.saveProvider(
            ProviderProfile(
                id = "codex-custom",
                name = "Codex",
                adapter = ProviderAdapter.CODEX_CLI,
                baseUrl = "",
                defaultModel = "gpt-5.6-luna",
            ),
        )

        assertEquals(listOf("gpt-5.6-luna"), catalog.selectableModels("codex-custom").map { it.id })
    }

    @Test
    fun `codex selection becomes selectable without a synthetic catalog model`() {
        val store = MapPreferenceStore()
        val catalog = ProviderCatalogService(store)
        catalog.saveProvider(
            ProviderProfile(
                id = "codex-custom",
                name = "Codex",
                adapter = ProviderAdapter.CODEX_CLI,
                baseUrl = "",
                models = listOf(ProviderModelConfig("stale-catalog-entry")),
            ),
        )
        catalog.setActiveProvider("codex-custom")
        catalog.setActiveSelection("codex-custom", "gpt-5.6-luna")
        val restored = ProviderCatalogService(store)

        assertEquals("gpt-5.6-luna", restored.getProvider("codex-custom")?.defaultModel)
        assertEquals(listOf("gpt-5.6-luna"), restored.selectableModels("codex-custom").map { it.id })
    }

    @Test
    fun `configured discovery persists codex display names and known metadata`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        catalog.saveProvider(
            ProviderProfile(
                id = "codex-custom",
                name = "Codex CLI",
                adapter = ProviderAdapter.CODEX_CLI,
                baseUrl = "",
                defaultModel = "gpt-codex",
                models = listOf(ProviderModelConfig("gpt-codex", name = "Old name", status = ModelStatus.BETA)),
            ),
        )

        catalog.updateDiscoveredModelConfigs(
            "codex-custom",
            listOf(
                ProviderModelConfig("gpt-codex", name = "Codex Updated"),
                ProviderModelConfig("gpt-new", name = "Codex New"),
            ),
        )

        val models = requireNotNull(catalog.getProvider("codex-custom")).models.associateBy(ProviderModelConfig::id)
        assertEquals("Codex Updated", models.getValue("gpt-codex").name)
        assertEquals(ModelStatus.BETA, models.getValue("gpt-codex").status)
        assertEquals("Codex New", models.getValue("gpt-new").name)
        assertEquals(ModelStatus.ACTIVE, models.getValue("gpt-new").status)
    }

    private class MapPreferenceStore : ProviderPreferenceStore {
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
