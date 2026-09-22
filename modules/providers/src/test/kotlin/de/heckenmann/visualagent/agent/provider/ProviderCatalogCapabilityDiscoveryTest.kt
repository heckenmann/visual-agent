package de.heckenmann.visualagent.agent.provider

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderCatalogCapabilityDiscoveryTest {
    @Test
    fun `complete provider capability discovery authoritatively replaces capabilities`() {
        val catalog = ProviderCatalogService(ProviderCatalogServiceTest.MapPreferenceStore())
        catalog.saveProvider(
            ProviderProfile(
                id = "ollama-capabilities",
                name = "Ollama capabilities",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                models = listOf(ProviderModelConfig("model", capabilities = setOf("vision"))),
            ),
        )

        catalog.updateModelCapabilities("ollama-capabilities", mapOf("model" to setOf("tools", "thinking")))

        val model = requireNotNull(catalog.getProvider("ollama-capabilities")).models.single()
        assertEquals(setOf("tools", "thinking"), model.capabilities)
        assertTrue(model.capabilitiesComplete)
    }

    @Test
    fun `capability discovery does not classify omitted models as complete`() {
        val catalog = ProviderCatalogService(ProviderCatalogServiceTest.MapPreferenceStore())
        catalog.saveProvider(
            ProviderProfile(
                id = "partial-capabilities",
                name = "Partial capabilities",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                models =
                    listOf(
                        ProviderModelConfig("reported"),
                        ProviderModelConfig("omitted", capabilities = setOf("vision")),
                    ),
            ),
        )

        catalog.updateModelCapabilities("partial-capabilities", mapOf("reported" to setOf("tools")))

        val omitted = requireNotNull(catalog.getProvider("partial-capabilities")).models.first { it.id == "omitted" }
        assertEquals(setOf("vision"), omitted.capabilities)
        assertFalse(omitted.capabilitiesComplete)
    }
}
