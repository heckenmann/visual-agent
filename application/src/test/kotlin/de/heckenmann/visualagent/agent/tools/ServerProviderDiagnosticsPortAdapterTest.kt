package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfigBean
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ServerProviderDiagnosticsPortAdapterTest {
    @Test
    fun `checks selected provider without mutating persisted model catalog`() {
        val preferences = InMemoryProviderPreferences()
        val catalog = ProviderCatalogService(preferences, AppConfigBean())
        catalog.saveProvider(
            ProviderProfile(
                id = "test-provider",
                name = "Test Provider",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://user:password@example.test/v1?key=query-secret",
                apiKey = "api-secret",
                defaultModel = "selected-model",
                models = listOf(ProviderModelConfig("selected-model")),
            ),
        )
        catalog.setActiveProvider("test-provider")
        val provider = mockk<LLMProvider>()
        every { provider.getModelConfigsReactive(any<ProviderProfile>()) } returns
            Mono.just(
                listOf(
                    ProviderModelConfig("selected-model", capabilities = setOf("vision", "tools"), capabilitiesComplete = true),
                    ProviderModelConfig("other-model"),
                ),
            )
        val before = preferences.values.toMap()

        val diagnostic = ServerProviderDiagnosticsPortAdapter(catalog, provider).check().block()!!

        assertEquals("test-provider", diagnostic.providerId)
        assertEquals(2, diagnostic.discoveredModelCount)
        assertEquals(true, diagnostic.selectedModelAvailable)
        assertEquals(true, diagnostic.credentialConfigured)
        assertEquals("https://example.test", diagnostic.endpointOrigin)
        assertEquals("selected-model", diagnostic.selectedModelId)
        assertEquals(listOf("tools", "vision"), diagnostic.selectedModelCapabilities)
        assertEquals(true, diagnostic.selectedModelCapabilitiesComplete)
        assertFalse(diagnostic.toString().contains("api-secret"))
        assertFalse(diagnostic.toString().contains("query-secret"))
        assertEquals(before, preferences.values)
    }

    @Test
    fun `normalizes provider errors and does not expose endpoint credentials`() {
        val preferences = InMemoryProviderPreferences()
        val catalog = ProviderCatalogService(preferences, AppConfigBean())
        catalog.saveProvider(
            ProviderProfile(
                id = "test-provider",
                name = "Test Provider",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://user:password@example.test/v1?key=query-secret",
                apiKey = "api-secret",
                defaultModel = "selected-model",
                models = listOf(ProviderModelConfig("selected-model")),
            ),
        )
        catalog.setActiveProvider("test-provider")
        val provider = mockk<LLMProvider>()
        every { provider.getModelConfigsReactive(any<ProviderProfile>()) } returns
            Mono.error(IllegalStateException("401 api-secret query-secret"))

        val result = ServerProviderDiagnosticsPortAdapter(catalog, provider).check().block()!!

        assertEquals("Authentication failed", result.failureKind)
        assertEquals(null, result.selectedModelAvailable)
        assertEquals(null, result.selectedModelCapabilities)
        assertFalse(result.toString().contains("api-secret"))
        assertFalse(result.toString().contains("query-secret"))
        assertFalse(result.toString().contains("password"))
    }

    @Test
    fun `distinguishes a missing selected model from unknown provider capabilities`() {
        val catalog = ProviderCatalogService(InMemoryProviderPreferences(), AppConfigBean())
        catalog.saveProvider(
            ProviderProfile(
                id = "test-provider",
                name = "Test Provider",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://example.test/v1",
                defaultModel = "selected-model",
                models = listOf(ProviderModelConfig("selected-model")),
            ),
        )
        catalog.setActiveProvider("test-provider")
        val provider = mockk<LLMProvider>()
        every { provider.getModelConfigsReactive(any<ProviderProfile>()) } returns
            Mono.just(listOf(ProviderModelConfig("other-model")))

        val result = ServerProviderDiagnosticsPortAdapter(catalog, provider).check().block()!!

        assertEquals(false, result.selectedModelAvailable)
        assertEquals(null, result.selectedModelCapabilities)
        assertEquals(null, result.selectedModelCapabilitiesComplete)
        assertEquals(null, result.failureKind)
    }

    private class InMemoryProviderPreferences : ProviderPreferenceStore {
        val values = mutableMapOf<String, String>()

        override fun getPreference(key: String): String? = values[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }
}
