package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

/** Verifies that connection checks use the selected provider profile. */
class ConfiguredLLMProviderConnectionTest {
    @Test
    fun `connection checks use the selected openai profile`() =
        runTest {
            val catalog = catalog()
            catalog.saveProvider(
                ProviderProfile(
                    id = "openai-custom",
                    name = "Custom OpenAI",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://custom.example.test",
                    apiKey = "profile-key",
                    defaultModel = "custom-model",
                    models = listOf(ProviderModelConfig("custom-model")),
                ),
            )
            catalog.setActiveSelection("openai-custom", "custom-model")
            val openAi = mockk<OpenAiClient>(relaxed = true)
            every { openAi.checkConnectionReactive(any<ProviderProfile>()) } returns Mono.just(true)
            val router = ConfiguredLLMProvider(mockk(relaxed = true), openAi, catalog)
            val profileSlot = slot<ProviderProfile>()

            assertEquals(true, router.checkConnectionReactive().awaitSingle())

            verify(exactly = 1) { openAi.checkConnectionReactive(capture(profileSlot)) }
            assertEquals("openai-custom", profileSlot.captured.id)
        }

    @Test
    fun `connection checks use the selected codex profile`() =
        runTest {
            val catalog = catalog()
            catalog.saveProvider(
                ProviderProfile(
                    id = "codex-custom",
                    name = "Custom Codex",
                    adapter = ProviderAdapter.CODEX_CLI,
                    baseUrl = "",
                    defaultModel = "codex-model",
                    models = listOf(ProviderModelConfig("codex-model")),
                ),
            )
            catalog.setActiveSelection("codex-custom", "codex-model")
            val codex = mockk<ProfiledProviderAdapter>()
            every { codex.adapter } returns ProviderAdapter.CODEX_CLI
            every { codex.checkConnectionReactive(any()) } returns Mono.just(true)
            val router =
                ConfiguredLLMProvider(
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    catalog,
                    profiledAdapters = listOf(codex),
                )
            val profileSlot = slot<ProviderProfile>()

            assertEquals(true, router.checkConnectionReactive().awaitSingle())

            verify(exactly = 1) { codex.checkConnectionReactive(capture(profileSlot)) }
            assertEquals("codex-custom", profileSlot.captured.id)
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
        )
}
