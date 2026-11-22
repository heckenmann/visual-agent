package de.heckenmann.visualagent.agent.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Catalog editing is conditional on persisted profiles, never on a UI copy of the active model. */
class ProviderProfileEditingTest {
    @Test fun `saving profile drafts retains a concurrently changed active selection`() {
        val catalog = catalog()
        val profiles = catalog.listProviders()
        catalog.setActiveProvider("openai")
        val currentModel = catalog.activeModelId()
        catalog.replaceProfiles(profiles.map { it.copy(name = "Edited ${it.name}") }, profiles)
        assertEquals("openai", catalog.activeProviderId())
        assertEquals(currentModel, catalog.activeModelId())
        assertEquals(true, catalog.listProviders().all { it.name.startsWith("Edited") })
    }

    @Test fun `stale profile drafts fail without erasing another catalog edit`() {
        val catalog = catalog()
        val baseline = catalog.listProviders()
        catalog.saveProvider(baseline.first().copy(name = "Updated elsewhere"))
        assertFailsWith<IllegalStateException> { catalog.replaceProfiles(baseline, baseline) }
        assertEquals("Updated elsewhere", catalog.getProvider(baseline.first().id)?.name)
    }

    @Test fun `removing or disabling the active provider requires an explicit model switch first`() {
        val catalog = catalog()
        val profiles = catalog.listProviders()
        assertFailsWith<IllegalArgumentException> {
            catalog.replaceProfiles(
                profiles.filterNot { it.id == catalog.activeProviderId() },
                profiles,
            )
        }
        assertEquals(profiles, catalog.listProviders())
    }

    @Test fun `an unavailable assignment does not prevent provider edits and can be cleared`() {
        val catalog = catalog()
        val provider = catalog.getProvider("ollama")!!
        catalog.saveProvider(provider.copy(models = listOf(ProviderModelConfig("vision", capabilities = setOf("vision")))))
        assertEquals("ollama" to "vision", catalog.taskModelAssignment(ModelCapability.VISION))
        catalog.updateDiscoveredModels("ollama", emptyList())
        val baseline = catalog.listProviders()
        catalog.replaceProfiles(baseline.map { it.copy(name = "Updated ${it.name}") }, baseline)
        val assignments = catalog.taskModelAssignments()
        catalog.replaceTaskModelAssignments(assignments - "VISION", assignments)
        assertEquals(null, catalog.taskModelAssignment(ModelCapability.VISION))
    }

    @Test fun `image generation gets an independent automatic model and preserves a manual selection`() {
        val catalog = catalog()
        val provider = catalog.getProvider("ollama")!!
        val first = ProviderModelConfig("image-a", capabilities = setOf("image_generation"))
        val second = ProviderModelConfig("image-b", capabilities = setOf("image_generation"))
        catalog.saveProvider(provider.copy(models = listOf(first, second)))

        assertEquals("ollama" to "image-a", catalog.taskModelAssignment(ModelCapability.IMAGE_GENERATION))
        val baseline = catalog.taskModelAssignments()
        catalog.replaceTaskModelAssignments(baseline + ("IMAGE_GENERATION" to ("ollama" to "image-b")), baseline)
        catalog.saveProvider(provider.copy(models = listOf(first, second), defaultModel = first.id))

        assertEquals("ollama" to "image-b", catalog.taskModelAssignment(ModelCapability.IMAGE_GENERATION))
    }

    private fun catalog(): ProviderCatalogService =
        ProviderCatalogService(
            object : ProviderPreferenceStore {
                private val values = mutableMapOf<String, String>()

                override fun getPreference(key: String): String? = values[key]

                override fun setPreference(
                    key: String,
                    value: String,
                ) {
                    values[key] = value
                }
            },
            DefaultProviderRuntimeConfig(),
        )
}
