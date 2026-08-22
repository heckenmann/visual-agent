package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Verifies that the live Codex catalog is loaded independently from UI visibility. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CodexModelCatalogInitializerTest {
    @Test
    fun `loads the active codex catalog after startup`() =
        runTest {
            val catalog = mockk<ProviderCatalogService>()
            val provider = mockk<ProfiledProviderAdapter>()
            every { catalog.activeProviderId() } returns PROVIDER_ID
            every { catalog.getProvider(PROVIDER_ID) } returns codexProfile()
            every { catalog.updateDiscoveredModelConfigs(PROVIDER_ID, any()) } returns Unit
            every { provider.adapter } returns ProviderAdapter.CODEX_CLI
            coEvery { provider.loadModels(any()) } returns listOf(ProviderModelConfig(id = "live-model"))

            CodexModelCatalogInitializer(catalog, listOf(provider), this, StandardTestDispatcher(testScheduler))
                .initializeActiveCodexCatalog()
            advanceUntilIdle()

            coVerify(exactly = 1) { provider.loadModels(any()) }
        }

    private fun codexProfile(): ProviderProfile =
        ProviderProfile(id = PROVIDER_ID, name = "Codex", adapter = ProviderAdapter.CODEX_CLI, baseUrl = "")

    private companion object {
        private const val PROVIDER_ID = "codex"
    }
}
