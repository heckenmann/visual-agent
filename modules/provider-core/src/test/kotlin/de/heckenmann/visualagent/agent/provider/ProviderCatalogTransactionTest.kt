package de.heckenmann.visualagent.agent.provider

import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies that runtime-provider publication follows a successful transaction commit. */
class ProviderCatalogTransactionTest {
    @Test
    fun `catalog changes publish runtime state and listeners after commit`() {
        val runtime = DefaultProviderRuntimeConfig()
        val catalog = ProviderCatalogService(InMemoryProviderPreferences(), runtime)
        var listenerCount = 0
        catalog.addChangeListener { listenerCount += 1 }
        val profile =
            ProviderProfile(
                id = "staged",
                name = "Staged",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "model-a",
                models = listOf(ProviderModelConfig("model-a")),
            )

        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.setActualTransactionActive(true)
        try {
            catalog.replaceConfiguration(ProviderConfiguration(listOf(profile), "staged", "model-a"))

            assertEquals("ollama", runtime.llmProvider)
            assertEquals(0, listenerCount)

            TransactionSynchronizationManager.getSynchronizations().forEach { synchronization -> synchronization.afterCommit() }

            assertEquals("staged", runtime.llmProvider)
            assertEquals(1, listenerCount)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
            TransactionSynchronizationManager.setActualTransactionActive(false)
        }
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
