package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeSettingsPanelSupportTest {
    @Test
    fun `provider profile form state conversion round trips`() {
        val profile =
            ProviderProfile(
                id = "custom",
                name = "Custom Provider",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://api.example.com",
                apiKey = "secret",
                enabled = true,
                defaultModel = "gpt-4o",
                options = mapOf("temperature" to "0.7", "top_p" to "0.9"),
                models =
                    listOf(
                        ProviderModelConfig("m1", "Model 1", ModelStatus.ACTIVE, mapOf("ctx" to "8k"), contextLimit = 8192),
                    ),
                modelWhitelist = setOf("m1"),
                modelBlacklist = setOf("old"),
            )

        val form = profile.toFormState()
        assertEquals("custom", form.id)
        assertEquals("Custom Provider", form.name)
        assertEquals(ProviderAdapter.OPENAI_COMPATIBLE, form.adapter)
        assertEquals("https://api.example.com", form.baseUrl)
        assertEquals("secret", form.apiKey)
        assertTrue(form.enabled)
        assertEquals("gpt-4o", form.defaultModel)
        assertEquals("temperature=0.7\ntop_p=0.9", form.optionsText)
        assertEquals("m1|ACTIVE|8192||ctx=8k", form.modelsText)
        assertEquals("m1", form.whitelistText)
        assertEquals("old", form.blacklistText)

        val restored = form.toProviderProfile(profile)
        assertEquals(profile.id, restored.id)
        assertEquals(profile.name, restored.name)
        assertEquals(profile.adapter, restored.adapter)
        assertEquals(profile.baseUrl, restored.baseUrl)
        assertEquals(profile.apiKey, restored.apiKey)
        assertEquals(profile.enabled, restored.enabled)
        assertEquals(profile.defaultModel, restored.defaultModel)
        assertEquals(profile.options, restored.options)
        assertEquals(profile.modelWhitelist, restored.modelWhitelist)
        assertEquals(profile.modelBlacklist, restored.modelBlacklist)
        assertEquals(1, restored.models.size)
        assertEquals("m1", restored.models.single().id)
    }

    @Test
    fun `new provider form state has defaults`() {
        val form = newProviderFormState()
        assertEquals(ProviderAdapter.OPENAI_COMPATIBLE, form.adapter)
        assertEquals("https://api.example.com", form.baseUrl)
        assertTrue(form.enabled)
        assertTrue(form.id.isBlank())
    }

    @Test
    fun `form validation errors`() {
        assertEquals("Provider ID is required.", ProviderProfileFormState().validationError())
        assertNotNull(ProviderProfileFormState(id = "bad id").validationError())
        assertNotNull(ProviderProfileFormState(id = "ok", name = "").validationError())
        assertNotNull(ProviderProfileFormState(id = "ok", name = "Name", baseUrl = "").validationError())
        assertNull(ProviderProfileFormState(id = "ok", name = "Name", baseUrl = "http://x").validationError())
    }

    @Test
    fun `settings map text conversion round trips`() {
        val map = mapOf("b" to "2", "a" to "1")
        assertEquals("a=1\nb=2", map.toSettingsMapText())
        assertEquals(map, "b=2\na=1".toSettingsMap())
        assertEquals(emptyMap(), "".toSettingsMap())
        assertEquals(mapOf("a" to "1"), "a=1\nnoequals\n".toSettingsMap())
    }

    @Test
    fun `provider models text conversion round trips`() {
        val models =
            listOf(
                ProviderModelConfig("m1", "Model 1", ModelStatus.BETA, mapOf("temp" to "0.5"), contextLimit = 4096, outputLimit = 512),
                ProviderModelConfig("m2", "Model 2", ModelStatus.DEPRECATED),
            )
        val text = models.toProviderModelsText()
        val parsed = text.toProviderModels()
        assertEquals(2, parsed.size)
        assertEquals("m1", parsed[0].id)
        assertEquals(ModelStatus.BETA, parsed[0].status)
        assertEquals(4096, parsed[0].contextLimit)
        assertEquals(512, parsed[0].outputLimit)
        assertEquals(mapOf("temp" to "0.5"), parsed[0].options)
        assertEquals("m2", parsed[1].id)
    }

    @Test
    fun `provider models parsing defaults invalid status to active`() {
        val parsed = "m1|UNKNOWN|||".toProviderModels()
        assertEquals(ModelStatus.ACTIVE, parsed.single().status)
    }

    @Test
    fun `csv text conversion round trips`() {
        assertEquals("a,b", setOf("b", "a").toCsvText())
        assertEquals(setOf("a", "b"), "b,a".toCsvSet())
        assertEquals(emptySet(), "".toCsvSet())
    }

    @Test
    fun `favorite model text conversion filters blanks and deduplicates`() {
        assertEquals("b,a", listOf("b", "a", "", "b").toFavoriteModelText())
        assertEquals(setOf("a", "b"), "b,a".toFavoriteModelSet())
    }

    @Test
    fun `model details text renders fields`() {
        val response =
            ShowResponse(
                model = "llama3",
                modifiedAt = "2024-01-01",
                details = ModelDetails(family = "llama", parameterSize = "8B", quantizationLevel = "Q4", format = "gguf"),
            )
        val text = response.toModelDetailsText()
        assertTrue("Model: llama3" in text)
        assertTrue("Family: llama" in text)
        assertTrue("Size: 8B" in text)
        assertTrue("Format: gguf" in text)
        assertTrue("Quantization: Q4" in text)
    }

    @Test
    fun `model details text handles missing details`() {
        val text = ShowResponse(model = "x", modifiedAt = "").toModelDetailsText()
        assertTrue("Model: x" in text)
    }

    @Test
    fun `save session settings persists active provider and mirrors to config`() {
        val catalog = mockk<ProviderCatalogService>(relaxed = true)
        val config = AppConfig.instance
        config.llmProvider = "openai"
        val profile =
            ProviderProfile(
                id = "openai",
                name = "OpenAI",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://api.openai.com",
                apiKey = "key",
                defaultModel = "gpt-4o-mini",
            )
        every { catalog.getProvider("openai") } returns profile

        saveSessionSettings(config, catalog, "openai", "gpt-4o", "https://api.example.com", "new-key")

        verify { catalog.setActiveProvider("openai") }
        verify { catalog.saveProvider(profile.copy(baseUrl = "https://api.example.com", apiKey = "new-key", defaultModel = "gpt-4o")) }
        assertEquals("openai", config.llmProvider)
        assertEquals("https://api.example.com", config.openAiBaseUrl)
        assertEquals("new-key", config.openAiApiKey)
        assertEquals("gpt-4o", config.openAiModel)
    }

    @Test
    fun `mirror provider to app config maps ollama fields`() {
        val config = AppConfig.instance
        mirrorProviderToAppConfig(
            config,
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11435",
                apiKey = "ollama-key",
                defaultModel = "llava",
            ),
        )
        assertEquals("http://localhost:11435", config.ollamaLocalUrl)
        assertEquals("ollama-key", config.ollamaApiKey)
        assertEquals("llava", config.ollamaModel)
    }

    @Test
    fun `font size clamping respects bounds`() {
        assertEquals(10, 5.clampFontSize())
        assertEquals(24, 30.clampFontSize())
        assertEquals(14, 14.clampFontSize())
    }

    @Test
    fun `to bounded int or null coerces inside range`() {
        assertNull("abc".toBoundedIntOrNull(1, 10))
        assertEquals(1, "0".toBoundedIntOrNull(1, 10))
        assertEquals(5, "5".toBoundedIntOrNull(1, 10))
        assertEquals(10, "15".toBoundedIntOrNull(1, 10))
        assertEquals(1, "-5".toBoundedIntOrNull(1, 10))
    }
}
