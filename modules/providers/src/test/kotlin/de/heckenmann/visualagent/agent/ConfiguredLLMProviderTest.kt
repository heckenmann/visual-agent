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
import io.mockk.verify
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfiguredLLMProviderTest {
    private val appConfig = TestProviderRuntimeConfig()

    @Test
    fun `missing active provider fails instead of silently falling back to ollama`() =
        runTest {
            val catalog = mockk<ProviderCatalogService>()
            every { catalog.activeProviderId() } returns "missing-provider"
            every { catalog.getProvider("missing-provider") } returns null
            every { catalog.resolve(any(), any(), any(), any()) } throws
                IllegalStateException("Active provider profile is missing: missing-provider")
            val router =
                ConfiguredLLMProvider(
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    catalog,
                )

            val error =
                assertFailsWith<IllegalStateException> {
                    router.chatReactive(listOf(Message("user", "hello"))).awaitSingle()
                }

            assertEquals("Active provider profile is missing: missing-provider", error.message)
        }

    @Test
    fun `chat delegates to openai provider with configured model`() =
        runTest {
            val originalProvider = appConfig.llmProvider
            val originalModel = appConfig.openAiModel
            try {
                appConfig.llmProvider = "openai"
                appConfig.openAiModel = "gpt-router"
                val ollama = mockk<OllamaClient>(relaxed = true)
                val openAi = mockk<OpenAiClient>()
                val requestSlot = io.mockk.slot<ChatRequestContext>()
                every { openAi.chatReactive(capture(requestSlot)) } returns
                    Mono.just(ChatResponse("gpt-router", Message("assistant", "ok"), true))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog())

                val response = router.chatReactive(ChatRequestContext(messages = listOf(Message("user", "hello")))).awaitSingle()

                assertEquals("ok", response.message.content)
                assertEquals("gpt-router", requestSlot.captured.model)
                verify(exactly = 1) { openAi.chatReactive(any<ChatRequestContext>()) }
                verify(exactly = 0) { ollama.chatReactive(any<ChatRequestContext>()) }
            } finally {
                appConfig.llmProvider = originalProvider
                appConfig.openAiModel = originalModel
            }
        }

    @Test
    fun `chatReactive resolves the catalog then delegates through the native provider contract`() {
        val ollama = mockk<OllamaClient>(relaxed = true)
        val openAi = mockk<OpenAiClient>()
        val requestSlot = io.mockk.slot<ChatRequestContext>()
        val catalog = catalog()
        catalog.setActiveSelection("openai", "gpt-router")
        every { openAi.chatReactive(capture(requestSlot)) } returns
            Mono.just(ChatResponse("gpt-router", Message("assistant", "ok"), true))
        val router = ConfiguredLLMProvider(ollama, openAi, catalog)

        StepVerifier
            .create(router.chatReactive(ChatRequestContext(messages = listOf(Message("user", "hello")))))
            .assertNext { assertEquals("ok", it.message.content) }
            .verifyComplete()

        assertEquals("openai", requestSlot.captured.provider)
    }

    @Test
    fun `models and connection delegate to ollama provider by default`() =
        runTest {
            val originalProvider = appConfig.llmProvider
            try {
                appConfig.llmProvider = "ollama"
                val ollama = mockk<OllamaClient>()
                val openAi = mockk<OpenAiClient>(relaxed = true)
                every { ollama.isConnected() } returns true
                every { ollama.checkConnectionReactive() } returns Mono.just(true)
                every { ollama.getModelsReactive(any<ProviderProfile>()) } returns Mono.just(listOf("llama"))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog(), fetchCapabilities = { Mono.just(emptyMap()) })

                assertEquals(true, router.isConnected())
                assertEquals(true, router.checkConnectionReactive().awaitSingle())
                assertEquals(listOf("llama"), router.getModelsReactive().awaitSingle())

                verify(exactly = 1) { ollama.checkConnectionReactive() }
                verify(exactly = 1) { ollama.getModelsReactive(any<ProviderProfile>()) }
                verify(exactly = 0) { openAi.getModelsReactive() }
            } finally {
                appConfig.llmProvider = originalProvider
            }
        }

    @Test
    fun `request provider override routes independently from session provider`() =
        runTest {
            val originalProvider = appConfig.llmProvider
            val originalModel = appConfig.openAiModel
            try {
                appConfig.llmProvider = "ollama"
                appConfig.openAiModel = "gpt-session"
                val ollama = mockk<OllamaClient>(relaxed = true)
                val openAi = mockk<OpenAiClient>()
                val requestSlot = io.mockk.slot<ChatRequestContext>()
                every { openAi.chatReactive(capture(requestSlot)) } returns
                    Mono.just(ChatResponse("gpt-agent", Message("assistant", "ok"), true))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog())

                router
                    .chatReactive(
                        ChatRequestContext(
                            messages = listOf(Message("user", "hello")),
                            provider = "openai",
                            model = "gpt-agent",
                            parameters = ModelParameters(temperature = 0.2, topP = 0.9, maxTokens = 1200),
                        ),
                    ).awaitSingle()

                assertEquals("gpt-agent", requestSlot.captured.model)
                assertEquals(0.2, requestSlot.captured.parameters.temperature)
                verify(exactly = 1) { openAi.chatReactive(any<ChatRequestContext>()) }
                verify(exactly = 0) { ollama.chatReactive(any<ChatRequestContext>()) }
            } finally {
                appConfig.llmProvider = originalProvider
                appConfig.openAiModel = originalModel
            }
        }

    @Test
    fun `agent request resolves its assigned provider instance and default model`() =
        runTest {
            val ollama = mockk<OllamaClient>(relaxed = true)
            val openAi = mockk<OpenAiClient>()
            val requestSlot = io.mockk.slot<ChatRequestContext>()
            every { openAi.chatReactive(capture(requestSlot)) } returns
                Mono.just(ChatResponse("gpt-work", Message("assistant", "ok"), true))
            val catalog = catalog()
            catalog.saveProvider(
                ProviderProfile(
                    id = "openai-work",
                    name = "OpenAI work",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://work.example.test",
                    defaultModel = "gpt-work",
                    models = listOf(ProviderModelConfig("gpt-work")),
                ),
            )
            val router = ConfiguredLLMProvider(ollama, openAi, catalog)

            router
                .chatReactive(
                    ChatRequestContext(
                        messages = listOf(Message("user", "hello")),
                        provider = "openai-work",
                    ),
                ).awaitSingle()

            assertEquals("openai-work", requestSlot.captured.providerProfile?.id)
            assertEquals("gpt-work", requestSlot.captured.model)
            verify(exactly = 1) { openAi.chatReactive(any<ChatRequestContext>()) }
            verify(exactly = 0) { ollama.chatReactive(any<ChatRequestContext>()) }
        }

    @Test
    fun `codex refresh persists the live model catalog`() =
        runTest {
            val catalog = catalog()
            catalog.saveProvider(
                ProviderProfile(
                    id = "codex-custom",
                    name = "Codex",
                    adapter = ProviderAdapter.CODEX_CLI,
                    baseUrl = "",
                    defaultModel = "gpt-5.6-luna",
                    models = listOf(ProviderModelConfig("gpt-5.6-luna", name = "Codex Luna")),
                ),
            )
            val codex = mockk<ProfiledProviderAdapter>()
            every { codex.adapter } returns ProviderAdapter.CODEX_CLI
            every { codex.loadModelsReactive(any()) } returns Mono.just(listOf(ProviderModelConfig("gpt-5.6-luna", name = "Codex Luna")))
            val router =
                ConfiguredLLMProvider(
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    catalog,
                    profiledAdapters = listOf(codex),
                )

            assertEquals(listOf("gpt-5.6-luna"), router.getModelsReactive("codex-custom").awaitSingle())
            assertEquals("Codex Luna", catalog.selectableModels("codex-custom").single().name)
            verify(exactly = 1) { codex.loadModelsReactive(any()) }
        }

    @Test
    fun `vision and embeddings delegate to active provider`() =
        runTest {
            val originalProvider = appConfig.llmProvider
            try {
                appConfig.llmProvider = "openai"
                val ollama = mockk<OllamaClient>(relaxed = true)
                val openAi = mockk<OpenAiClient>(relaxed = true)
                val catalog = catalog()
                catalog.setActiveSelection("openai", "gpt-vision")
                every { openAi.visionReactive(any(), any(), "gpt-vision") } returns
                    Mono.just(ChatResponse("gpt", Message("assistant", "image ok"), done = true))
                every { openAi.embeddingsReactive("text", "gpt-vision") } returns Mono.just(listOf(0.1, 0.2))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog)

                assertEquals(
                    "image ok",
                    router
                        .visionReactive(ByteArray(0), "describe")
                        .awaitSingle()
                        .message.content,
                )
                assertEquals(listOf(0.1, 0.2), router.embeddingsReactive("text").awaitSingle())

                verify(exactly = 1) { openAi.visionReactive(any(), any(), "gpt-vision") }
                verify(exactly = 1) { openAi.embeddingsReactive("text", "gpt-vision") }
            } finally {
                appConfig.llmProvider = originalProvider
            }
        }

    @Test
    fun `chat and stream with message list delegate to active provider`() =
        runTest {
            val originalProvider = appConfig.llmProvider
            try {
                appConfig.llmProvider = "ollama"
                val ollama = mockk<OllamaClient>(relaxed = true)
                val openAi = mockk<OpenAiClient>(relaxed = true)
                every { ollama.chatReactive(any<ChatRequestContext>()) } returns
                    Mono.just(ChatResponse("llama", Message("assistant", "ok"), true))
                every { ollama.streamReactive(any<ChatRequestContext>()) } returns
                    Flux.just(ChatResponse("llama", Message("assistant", "c"), true))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog())

                val chatResponse = router.chatReactive(listOf(Message("user", "hi"))).awaitSingle()
                assertEquals("ok", chatResponse.message.content)

                val streamChunks = router.streamReactive(listOf(Message("user", "hi"))).collectList().awaitSingle()
                assertEquals("c", streamChunks.single().message.content)
            } finally {
                appConfig.llmProvider = originalProvider
            }
        }

    @Test
    fun `message list overload uses active openai provider and model`() =
        runTest {
            val ollama = mockk<OllamaClient>(relaxed = true)
            val openAi = mockk<OpenAiClient>()
            val requestSlot = io.mockk.slot<ChatRequestContext>()
            every { openAi.chatReactive(capture(requestSlot)) } returns
                Mono.just(ChatResponse("gpt-active", Message("assistant", "ok"), true))
            val catalog = catalog()
            catalog.setActiveSelection("openai", "gpt-active")
            val router = ConfiguredLLMProvider(ollama, openAi, catalog)

            val response = router.chatReactive(listOf(Message("user", "hello"))).awaitSingle()

            assertEquals("ok", response.message.content)
            assertEquals("openai", requestSlot.captured.provider)
            assertEquals("gpt-active", requestSlot.captured.model)
            verify(exactly = 1) { openAi.chatReactive(any<ChatRequestContext>()) }
            verify(exactly = 0) { ollama.chatReactive(any<ChatRequestContext>()) }
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
