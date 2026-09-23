package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies provider model discovery and reactive streaming through the facade. */
class ConfiguredLLMProviderDiscoveryTest {
    private val appConfig = TestProviderRuntimeConfig()

    @Test
    fun `Ollama discovery marks only reported models complete`() =
        runTest {
            val profile = ProviderProfile("ollama", "Ollama", ProviderAdapter.OLLAMA, "http://localhost:11434")
            val ollama = mockk<OllamaClient>()
            every { ollama.getModelsReactive(profile) } returns Mono.just(listOf("reported", "unknown"))
            val router =
                ConfiguredLLMProvider(
                    ollama,
                    mockk(),
                    catalog(),
                    fetchCapabilities = { Mono.just(mapOf("reported" to setOf("completion", "tools", "vision"))) },
                )

            val models = router.getModelConfigsReactive(profile).awaitSingle()

            assertEquals(setOf("completion", "tools", "vision"), models.first().capabilities)
            assertTrue(models.first().capabilitiesComplete)
            assertFalse(models.last().capabilitiesComplete)
            assertEquals(emptySet(), models.last().capabilities)
        }

    @Test
    fun `openai profile supports discovery details and streaming`() =
        runTest {
            val originalProvider = appConfig.llmProvider
            try {
                appConfig.llmProvider = "openai"
                val catalog = catalog()
                val ollama = mockk<OllamaClient>(relaxed = true)
                val openAi = mockk<OpenAiClient>()
                every { openAi.getModelsReactive(any<ProviderProfile>()) } returns Mono.just(listOf("gpt-profile"))
                every { openAi.getModelDetailsReactive(any<ProviderProfile>(), "gpt-profile") } returns
                    Mono.just(ShowResponse("gpt-profile", "", details = ModelDetails(family = "openai")))
                every { openAi.streamReactive(any<ChatRequestContext>()) } returns
                    Flux.just(ChatResponse("gpt-profile", Message("assistant", "chunk"), true))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog)

                assertEquals(listOf("gpt-profile"), router.getModelsReactive("openai").awaitSingle())
                assertEquals("gpt-profile", router.getModelDetailsReactive("openai", "gpt-profile").awaitSingle().model)
                val chunks =
                    router
                        .streamReactive(
                            ChatRequestContext(
                                messages = listOf(Message("user", "hello")),
                                provider = "openai",
                                model = "gpt-profile",
                            ),
                        ).collectList()
                        .awaitSingle()
                assertEquals("chunk", chunks.single().message.content)
            } finally {
                appConfig.llmProvider = originalProvider
            }
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
            appConfig,
        )
}
