package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.ConfiguredLLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class CodexModelCatalogInitializerTest {
    @Test
    fun `loads empty active codex catalog after startup`() =
        runTest {
            val catalog = mockk<ProviderCatalogService>()
            val provider = mockk<ConfiguredLLMProvider>()
            every { catalog.activeProviderId() } returns PROVIDER_ID
            every { catalog.getProvider(PROVIDER_ID) } returns codexProfile()
            every { catalog.selectableModels(PROVIDER_ID) } returns emptyList()
            coEvery { provider.getModels(PROVIDER_ID) } returns listOf("gpt-codex")

            CodexModelCatalogInitializer(catalog, provider, this).initializeActiveCodexCatalog()
            advanceUntilIdle()

            coVerify(exactly = 1) { provider.getModels(PROVIDER_ID) }
        }

    @Test
    fun `refreshes populated codex catalog after startup`() =
        runTest {
            val catalog = mockk<ProviderCatalogService>()
            val provider = mockk<ConfiguredLLMProvider>()
            every { catalog.activeProviderId() } returns PROVIDER_ID
            every { catalog.getProvider(PROVIDER_ID) } returns codexProfile()
            every { catalog.selectableModels(PROVIDER_ID) } returns listOf(ProviderModelConfig("gpt-codex"))
            coEvery { provider.getModels(PROVIDER_ID) } returns listOf("gpt-codex", "codex-auto-review")

            CodexModelCatalogInitializer(catalog, provider, this).initializeActiveCodexCatalog()
            advanceUntilIdle()

            coVerify(exactly = 1) { provider.getModels(PROVIDER_ID) }
        }

    private fun codexProfile(): ProviderProfile =
        ProviderProfile(
            id = PROVIDER_ID,
            name = "Codex",
            adapter = ProviderAdapter.CODEX_CLI,
            baseUrl = "",
        )

    private companion object {
        private const val PROVIDER_ID = "codex"
    }
}
