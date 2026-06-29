package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.PreferenceStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComposeSettingsPanelSupportTest {
    @Test
    fun `provider options and model rows round trip through text format`() {
        val models =
            listOf(
                ProviderModelConfig(
                    id = "model-a",
                    status = ModelStatus.BETA,
                    contextLimit = 8192,
                    outputLimit = 2048,
                    options = mapOf("temperature" to "0.2", "topP" to "0.8"),
                ),
            )

        assertEquals(mapOf("reasoningEffort" to "medium"), "reasoningEffort=medium".toSettingsMap())
        assertEquals(models, models.toProviderModelsText().toProviderModels())
        assertEquals(setOf("a", "b"), "a, b".toCsvSet())
    }

    @Test
    fun `provider profile validation catches required fields`() {
        assertEquals("Provider ID is required.", ProviderProfileFormState().validationError())
        assertEquals("Provider ID contains invalid characters.", ProviderProfileFormState(id = "not valid").validationError())
        assertEquals("Name is required.", ProviderProfileFormState(id = "valid").validationError())
        assertEquals("Base URL is required.", ProviderProfileFormState(id = "valid", name = "Valid").validationError())
        assertNull(ProviderProfileFormState(id = "valid", name = "Valid", baseUrl = "https://example.test").validationError())
    }

    @Test
    fun `standard providers mirror settings to legacy app config fields`() {
        val config = AppConfig.instance
        val snapshot = ConfigSnapshot(config)
        try {
            mirrorProviderToAppConfig(
                config,
                ProviderProfile(
                    id = "ollama",
                    name = "Ollama",
                    adapter = ProviderAdapter.OLLAMA,
                    baseUrl = "http://ollama.test",
                    apiKey = "ollama-key",
                    defaultModel = "llama-test",
                ),
            )
            assertEquals("ollama", config.llmProvider)
            assertEquals("http://ollama.test", config.ollamaLocalUrl)
            assertEquals("ollama-key", config.ollamaApiKey)
            assertEquals("llama-test", config.ollamaModel)

            mirrorProviderToAppConfig(
                config,
                ProviderProfile(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://openai.test",
                    apiKey = "openai-key",
                    defaultModel = "gpt-test",
                ),
            )
            assertEquals("openai", config.llmProvider)
            assertEquals("https://openai.test", config.openAiBaseUrl)
            assertEquals("openai-key", config.openAiApiKey)
            assertEquals("gpt-test", config.openAiModel)
        } finally {
            snapshot.restore(config)
        }
    }

    @Test
    fun `custom provider remains catalog backed without overwriting standard fields`() {
        val config = AppConfig.instance
        val snapshot = ConfigSnapshot(config)
        val catalog = ProviderCatalogService(MapPreferenceStore())
        try {
            config.ollamaLocalUrl = "http://original-ollama"
            config.openAiBaseUrl = "https://original-openai"
            catalog.saveProvider(
                ProviderProfile(
                    id = "gateway",
                    name = "Gateway",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://gateway.test",
                    defaultModel = "gpt-gateway",
                ),
            )

            saveSessionSettings(
                config = config,
                providerCatalog = catalog,
                providerId = "gateway",
                modelId = "gpt-gateway",
                baseUrl = "https://gateway.test/v1",
                apiKey = "gateway-key",
            )

            assertEquals("gateway", config.llmProvider)
            assertEquals("http://original-ollama", config.ollamaLocalUrl)
            assertEquals("https://original-openai", config.openAiBaseUrl)
            assertEquals("https://gateway.test/v1", catalog.getProvider("gateway")?.baseUrl)
            assertEquals("gateway-key", catalog.getProvider("gateway")?.apiKey)
        } finally {
            snapshot.restore(config)
        }
    }

    @Test
    fun `favorite model text is stable and font size is clamped`() {
        assertEquals("a,b", listOf("b", "a", "a", "").sorted().toFavoriteModelText())
        assertEquals(10, 4.clampFontSize())
        assertEquals(24, 99.clampFontSize())
        assertEquals(16, 16.clampFontSize())
    }

    private data class ConfigSnapshot(
        val llmProvider: String,
        val ollamaLocalUrl: String,
        val ollamaModel: String,
        val ollamaApiKey: String,
        val openAiBaseUrl: String,
        val openAiModel: String,
        val openAiApiKey: String,
    ) {
        constructor(config: AppConfig) : this(
            llmProvider = config.llmProvider,
            ollamaLocalUrl = config.ollamaLocalUrl,
            ollamaModel = config.ollamaModel,
            ollamaApiKey = config.ollamaApiKey,
            openAiBaseUrl = config.openAiBaseUrl,
            openAiModel = config.openAiModel,
            openAiApiKey = config.openAiApiKey,
        )

        fun restore(config: AppConfig) {
            config.llmProvider = llmProvider
            config.ollamaLocalUrl = ollamaLocalUrl
            config.ollamaModel = ollamaModel
            config.ollamaApiKey = ollamaApiKey
            config.openAiBaseUrl = openAiBaseUrl
            config.openAiModel = openAiModel
            config.openAiApiKey = openAiApiKey
        }
    }

    private class MapPreferenceStore : PreferenceStore {
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
