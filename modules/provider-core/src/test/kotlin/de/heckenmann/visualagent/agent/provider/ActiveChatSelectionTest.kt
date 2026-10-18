package de.heckenmann.visualagent.agent.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Prevents a configuration catalog from activating explicitly non-chat models. */
class ActiveChatSelectionTest {
    @Test
    fun `reject unsupported chat before writing and accept an explicit correction`() {
        val values = mutableMapOf<String, String>()
        val preferences =
            object : ProviderPreferenceStore {
                override fun getPreference(key: String): String? = values[key]

                override fun setPreference(
                    key: String,
                    value: String,
                ) {
                    values[key] = value
                }
            }
        val catalog = ProviderCatalogService(preferences)
        val before = values.toMap()
        val model =
            ProviderModelConfig("legacy", capabilities = setOf("vision"), capabilitiesComplete = true, capabilitiesOverridden = true)
        val profile = ProviderProfile("custom", "Custom", ProviderAdapter.CODEX_CLI, "", models = listOf(model))
        val configuration = ProviderConfiguration(catalog.listProviders() + profile, profile.id, model.id)
        assertFailsWith<IllegalArgumentException> { catalog.replaceConfiguration(configuration) }
        assertEquals(before, values)
        val corrected = profile.copy(models = listOf(model.copy(capabilityOverrides = mapOf("chat" to true))))
        catalog.replaceConfiguration(configuration.copy(providers = catalog.listProviders() + corrected))
        assertEquals(model.id, catalog.activeModelId())
    }
}
