package de.heckenmann.visualagent.agent.provider

import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.PreferenceStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProviderCatalogServiceTest {
    @Test
    fun `legacy settings migrate into persisted provider profiles`() {
        val store = MapPreferenceStore()
        val catalog = ProviderCatalogService(store, AppConfigBean(store))

        assertNotNull(catalog.getProvider("ollama"))
        assertNotNull(catalog.getProvider("openai"))
        assertNotNull(store.getPreference("llm.provider.catalog.v1"))
    }

    @Test
    fun `model filters exclude disabled deprecated and blacklisted models`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        catalog.saveProvider(
            ProviderProfile(
                id = "filtered",
                name = "Filtered",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://example.test",
                defaultModel = "active",
                models =
                    listOf(
                        ProviderModelConfig("active"),
                        ProviderModelConfig("deprecated", status = ModelStatus.DEPRECATED),
                        ProviderModelConfig("disabled", status = ModelStatus.DISABLED),
                        ProviderModelConfig("blocked"),
                    ),
                modelBlacklist = setOf("blocked"),
            ),
        )

        assertEquals(listOf("active"), catalog.selectableModels("filtered").map { it.id })
    }

    @Test
    fun `multiple profiles using one adapter persist independently`() {
        val store = MapPreferenceStore()
        val catalog = ProviderCatalogService(store)
        catalog.saveProvider(
            ProviderProfile(
                id = "openai-work",
                name = "OpenAI work",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://work.example.test",
                defaultModel = "gpt-work",
                models = listOf(ProviderModelConfig("gpt-work")),
            ),
        )
        catalog.saveProvider(
            ProviderProfile(
                id = "openai-personal",
                name = "OpenAI personal",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://personal.example.test",
                defaultModel = "gpt-personal",
                models = listOf(ProviderModelConfig("gpt-personal")),
            ),
        )

        val restored = ProviderCatalogService(store)

        assertEquals(
            listOf("openai-personal", "openai-work"),
            restored.listProviders().filter { it.id in setOf("openai-personal", "openai-work") }.map { it.id },
        )
        assertEquals("gpt-work", restored.resolve("openai-work", null).model.id)
        assertEquals("gpt-personal", restored.resolve("openai-personal", null).model.id)
    }

    @Test
    fun `main agent model selection persists without changing provider profile`() {
        val store = MapPreferenceStore()
        val catalog = ProviderCatalogService(store)
        val profile =
            ProviderProfile(
                id = "openai-custom",
                name = "OpenAI Custom",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://custom.example.test",
                defaultModel = "profile-default",
            )
        catalog.saveProvider(profile)

        catalog.setActiveSelection(profile.id, "main-agent-model")

        assertEquals("profile-default", catalog.getProvider(profile.id)?.defaultModel)
        assertEquals("main-agent-model", catalog.activeModelId())
        assertEquals("main-agent-model", catalog.resolve(null, null).model.id)
        val restored = ProviderCatalogService(store)
        assertEquals(profile.id, restored.activeProviderId())
        assertEquals("main-agent-model", restored.activeModelId())
        assertEquals("profile-default", restored.getProvider(profile.id)?.defaultModel)
    }

    @Test
    fun `catalog seeds the codex profile without selecting it`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        val profile = requireNotNull(catalog.getProvider(ProviderEnvironmentCredentials.CODEX_PROFILE_ID))

        assertEquals("OpenAI ChatGPT Codex", profile.name)
        assertEquals("https://api.openai.com/v1", profile.baseUrl)
        assertEquals("ollama", catalog.activeProviderId())
    }

    @Test
    fun `options merge from provider through variant`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        catalog.saveProvider(
            ProviderProfile(
                id = "custom",
                name = "Custom",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://example.test",
                defaultModel = "model",
                options = mapOf("temperature" to "0.1", "shared" to "provider"),
                models =
                    listOf(
                        ProviderModelConfig(
                            id = "model",
                            options = mapOf("topP" to "0.8", "shared" to "model"),
                            variants = mapOf("precise" to mapOf("temperature" to "0.2", "shared" to "variant")),
                            outputLimit = 4096,
                        ),
                    ),
            ),
        )

        val resolved =
            catalog.resolve(
                providerId = "custom",
                modelId = "model",
                variant = "precise",
                agentOptions = mapOf("topP" to "0.6", "shared" to "agent"),
            )

        assertEquals("0.2", resolved.options["temperature"])
        assertEquals("0.6", resolved.options["topP"])
        assertEquals("variant", resolved.options["shared"])
        assertEquals("4096", resolved.options["maxTokens"])
    }

    @Test
    fun `deleting active provider selects another enabled profile`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        catalog.setActiveProvider("openai")

        assertEquals(true, catalog.deleteProvider("openai"))
        assertFalse(catalog.activeProviderId() == "openai")
    }

    @Test
    fun `disabling active provider selects another enabled profile`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        catalog.setActiveProvider("openai")
        val openAi = requireNotNull(catalog.getProvider("openai"))

        catalog.saveProvider(openAi.copy(enabled = false))

        assertEquals("ollama", catalog.activeProviderId())
        assertFalse(requireNotNull(catalog.getProvider("openai")).enabled)
    }

    @Test
    fun `resolve falls back to first selectable when default model is unavailable`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        catalog.saveProvider(
            ProviderProfile(
                id = "stale",
                name = "Stale",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "missing-on-server",
                models = listOf(ProviderModelConfig("qwen3-coder-next:cloud"), ProviderModelConfig("mistral-large-3:675b-cloud")),
            ),
        )

        val resolved = catalog.resolve(providerId = "stale", modelId = null)

        assertEquals("qwen3-coder-next:cloud", resolved.model.id)
    }

    @Test
    fun `resolve rejects explicit model that is blacklisted`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        catalog.saveProvider(
            ProviderProfile(
                id = "explicit",
                name = "Explicit",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "available",
                models = listOf(ProviderModelConfig("available"), ProviderModelConfig("blocked")),
                modelBlacklist = setOf("blocked"),
            ),
        )

        val error =
            runCatching { catalog.resolve(providerId = "explicit", modelId = "blocked") }
                .exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message.orEmpty().contains("blocked"))
    }

    @Test
    fun `discovery preserves metadata for known models`() {
        val catalog = ProviderCatalogService(MapPreferenceStore())
        catalog.saveProvider(
            ProviderProfile(
                id = "custom",
                name = "Custom",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "https://example.test",
                models = listOf(ProviderModelConfig("known", status = ModelStatus.BETA, outputLimit = 1000)),
            ),
        )

        catalog.updateDiscoveredModels("custom", listOf("known", "new"))

        assertEquals(
            ModelStatus.BETA,
            catalog
                .getProvider("custom")
                ?.models
                ?.first { it.id == "known" }
                ?.status,
        )
        assertEquals(
            ModelStatus.ACTIVE,
            catalog
                .getProvider("custom")
                ?.models
                ?.first { it.id == "new" }
                ?.status,
        )
        assertFalse(catalog.deleteProvider("missing"))
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
