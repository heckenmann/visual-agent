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
