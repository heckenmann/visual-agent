package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfigBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerConfigurationDiagnosticsPortAdapterTest {
    @Test
    fun `reports active catalog configuration without endpoint credentials or path`() {
        val preferences = InMemoryProviderPreferences()
        val catalog = ProviderCatalogService(preferences, AppConfigBean())
        catalog.saveProvider(
            ProviderProfile(
                id = "custom-provider",
                name = "Custom Provider",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://username:password@example.test:9443/private/path?token=query-secret",
                apiKey = "api-secret",
                defaultModel = "model-1",
                models =
                    listOf(
                        ProviderModelConfig(
                            id = "model-1",
                            status = ModelStatus.ACTIVE,
                            contextLimit = 64_000,
                            outputLimit = 4_000,
                            capabilities = setOf("tools", "vision"),
                            capabilitiesComplete = true,
                        ),
                    ),
            ),
        )
        catalog.setActiveProvider("custom-provider")
        val config =
            AppConfigBean().apply {
                contextLength = 32_000
                timeoutSeconds = 80
                maxParallelSubAgents = 3
            }

        val snapshot = ServerConfigurationDiagnosticsPortAdapter(catalog, config).snapshot()

        assertEquals("https://example.test:9443", snapshot.provider.endpointOrigin)
        assertEquals("model-1", snapshot.provider.modelId)
        assertEquals(64_000, snapshot.provider.modelContextLimit)
        assertTrue(snapshot.provider.apiKeyConfigured)
        assertTrue(snapshot.provider.modelCapabilitiesComplete)
        assertEquals(32_000, snapshot.contextWindow)
        assertTrue(snapshot.warnings.isEmpty())
        assertTrue(snapshot.warningCodes.isEmpty())
        val serialized = snapshot.toString()
        assertFalse(serialized.contains("username"))
        assertFalse(serialized.contains("password"))
        assertFalse(serialized.contains("private/path"))
        assertFalse(serialized.contains("query-secret"))
        assertFalse(serialized.contains("api-secret"))
    }

    @Test
    fun `warns when the selected model is no longer in the provider catalog`() {
        val preferences = InMemoryProviderPreferences()
        val catalog = ProviderCatalogService(preferences, AppConfigBean())
        val profile = catalog.listProviders().first()
        catalog.saveProvider(profile.copy(models = profile.models.filterNot { it.id == catalog.activeModelId() }))

        val snapshot = ServerConfigurationDiagnosticsPortAdapter(catalog, AppConfigBean()).snapshot()

        assertTrue(snapshot.warnings.any { it.contains("not present") })
        assertTrue("active_model_missing" in snapshot.warningCodes)
    }
}

private class InMemoryProviderPreferences : ProviderPreferenceStore {
    private val values = mutableMapOf<String, String>()

    override fun getPreference(key: String): String? = values[key]

    override fun setPreference(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
