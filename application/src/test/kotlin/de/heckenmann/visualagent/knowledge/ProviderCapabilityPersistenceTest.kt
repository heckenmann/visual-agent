package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.provider.ModelCapability
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.supportsCapability
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies targeted overrides survive database-backed catalog reloads and capability refresh. */
class ProviderCapabilityPersistenceTest {
    @Test
    fun `catalog reload and provider refresh preserve independent user choices`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:capability-overrides").use { db ->
            val first = ProviderCatalogService(db.preferenceStore)
            val model = ProviderModelConfig("model", capabilities = setOf("vision"), capabilityOverrides = mapOf("vision" to false))
            first.saveProvider(
                ProviderProfile("custom", "Custom", ProviderAdapter.CODEX_CLI, "", defaultModel = "model", models = listOf(model)),
            )
            first.setActiveSelection("custom", "model")
            val reloaded = ProviderCatalogService(db.preferenceStore)
            assertEquals(model, reloaded.getProvider("custom")!!.models.single())
            assertTrue(reloaded.activeModelSupportsToolCalling())
            reloaded.updateModelCapabilities("custom", mapOf("model" to setOf("completion", "vision", "tools")))
            val refreshed = ProviderCatalogService(db.preferenceStore).getProvider("custom")!!.models.single()
            assertTrue(refreshed.supportsCapability(ModelCapability.CHAT))
            assertFalse(refreshed.supportsCapability(ModelCapability.VISION))
            assertEquals(mapOf("vision" to false), refreshed.capabilityOverrides)
            first.saveProvider(
                first.getProvider("custom")!!.copy(
                    models =
                        listOf(
                            refreshed.copy(
                                capabilityOverrides =
                                    mapOf(
                                        "tools" to false,
                                    ),
                            ),
                        ),
                ),
            )
            assertFalse(reloaded.activeModelSupportsToolCalling())
        }
    }
}
