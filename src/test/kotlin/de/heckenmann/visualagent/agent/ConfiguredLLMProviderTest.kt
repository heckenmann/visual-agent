package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.PreferenceStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ConfiguredLLMProviderTest {
    private val appConfig = AppConfigBean()

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
                coEvery { openAi.chat(capture(requestSlot)) } returns ChatResponse("gpt-router", Message("assistant", "ok"), true)
                val router = ConfiguredLLMProvider(ollama, openAi, catalog())

                val response = router.chat(ChatRequestContext(messages = listOf(Message("user", "hello"))))

                assertEquals("ok", response.message.content)
                assertEquals("gpt-router", requestSlot.captured.model)
                coVerify(exactly = 1) { openAi.chat(any<ChatRequestContext>()) }
                coVerify(exactly = 0) { ollama.chat(any<ChatRequestContext>()) }
            } finally {
                appConfig.llmProvider = originalProvider
                appConfig.openAiModel = originalModel
            }
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
                coEvery { ollama.checkConnection() } returns true
                coEvery { ollama.getModels(any<ProviderProfile>()) } returns listOf("llama")
                coEvery { ollama.stream(any<ChatRequestContext>()) } returns
                    flowOf(ChatResponse("llama", Message("assistant", "chunk"), true))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog(), fetchCapabilities = { emptyMap() })

                assertEquals(true, router.isConnected())
                assertEquals(true, router.checkConnection())
                assertEquals(listOf("llama"), router.getModels())

                coVerify(exactly = 2) { ollama.getModels(any<ProviderProfile>()) }
                coVerify(exactly = 0) { openAi.getModels() }
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
                coEvery { openAi.chat(capture(requestSlot)) } returns ChatResponse("gpt-agent", Message("assistant", "ok"), true)
                val router = ConfiguredLLMProvider(ollama, openAi, catalog())

                router.chat(
                    ChatRequestContext(
                        messages = listOf(Message("user", "hello")),
                        provider = "openai",
                        model = "gpt-agent",
                        parameters = ModelParameters(temperature = 0.2, topP = 0.9, maxTokens = 1200),
                    ),
                )

                assertEquals("gpt-agent", requestSlot.captured.model)
                assertEquals(0.2, requestSlot.captured.parameters.temperature)
                coVerify(exactly = 1) { openAi.chat(any<ChatRequestContext>()) }
                coVerify(exactly = 0) { ollama.chat(any<ChatRequestContext>()) }
            } finally {
                appConfig.llmProvider = originalProvider
                appConfig.openAiModel = originalModel
            }
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
                coEvery { openAi.getModels(any<ProviderProfile>()) } returns listOf("gpt-profile")
                coEvery { openAi.getModelDetails(any<ProviderProfile>(), "gpt-profile") } returns
                    ShowResponse("gpt-profile", "", details = ModelDetails(family = "openai"))
                coEvery { openAi.stream(any<ChatRequestContext>()) } returns
                    flowOf(ChatResponse("gpt-profile", Message("assistant", "chunk"), true))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog)

                assertEquals(listOf("gpt-profile"), router.getModels("openai"))
                assertEquals("gpt-profile", router.getModelDetails("openai", "gpt-profile").model)
                val chunks =
                    router
                        .stream(
                            ChatRequestContext(
                                messages = listOf(Message("user", "hello")),
                                provider = "openai",
                                model = "gpt-profile",
                            ),
                        ).toList()
                assertEquals("chunk", chunks.single().message.content)
            } finally {
                appConfig.llmProvider = originalProvider
            }
        }

    @Test
    fun `vision and embeddings delegate to active provider`() =
        runTest {
            val originalProvider = appConfig.llmProvider
            try {
                appConfig.llmProvider = "openai"
                val ollama = mockk<OllamaClient>(relaxed = true)
                val openAi = mockk<OpenAiClient>(relaxed = true)
                coEvery { openAi.vision(any(), any()) } returns
                    ChatResponse("gpt", Message("assistant", "image ok"), done = true)
                coEvery { openAi.embeddings("text") } returns listOf(0.1, 0.2)
                val router = ConfiguredLLMProvider(ollama, openAi, catalog())

                assertEquals("image ok", router.vision(ByteArray(0), "describe").message.content)
                assertEquals(listOf(0.1, 0.2), router.embeddings("text"))

                coVerify(exactly = 1) { openAi.vision(any(), any()) }
                coVerify(exactly = 1) { openAi.embeddings("text") }
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
                coEvery { ollama.chat(any<List<Message>>()) } returns ChatResponse("llama", Message("assistant", "ok"), true)
                coEvery { ollama.stream(any<List<Message>>()) } returns flowOf(ChatResponse("llama", Message("assistant", "c"), true))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog())

                val chatResponse = router.chat(listOf(Message("user", "hi")))
                assertEquals("ok", chatResponse.message.content)

                val streamChunks = router.stream(listOf(Message("user", "hi"))).toList()
                assertEquals("c", streamChunks.single().message.content)
            } finally {
                appConfig.llmProvider = originalProvider
            }
        }

    private fun catalog(): ProviderCatalogService =
        ProviderCatalogService(
            object : PreferenceStore {
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
