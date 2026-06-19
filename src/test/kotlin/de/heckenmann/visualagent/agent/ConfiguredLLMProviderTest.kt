package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfig
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
    @Test
    fun `chat delegates to openai provider with configured model`() =
        runTest {
            val originalProvider = AppConfig.instance.llmProvider
            val originalModel = AppConfig.instance.openAiModel
            try {
                AppConfig.instance.llmProvider = "openai"
                AppConfig.instance.openAiModel = "gpt-router"
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
                AppConfig.instance.llmProvider = originalProvider
                AppConfig.instance.openAiModel = originalModel
            }
        }

    @Test
    fun `models and connection delegate to ollama provider by default`() =
        runTest {
            val originalProvider = AppConfig.instance.llmProvider
            try {
                AppConfig.instance.llmProvider = "ollama"
                val ollama = mockk<OllamaClient>()
                val openAi = mockk<OpenAiClient>(relaxed = true)
                every { ollama.isConnected() } returns true
                coEvery { ollama.checkConnection() } returns true
                coEvery { ollama.getModels(any<ProviderProfile>()) } returns listOf("llama")
                coEvery { ollama.stream(any<ChatRequestContext>()) } returns
                    flowOf(ChatResponse("llama", Message("assistant", "chunk"), true))
                val router = ConfiguredLLMProvider(ollama, openAi, catalog())

                assertEquals(true, router.isConnected())
                assertEquals(true, router.checkConnection())
                assertEquals(listOf("llama"), router.getModels())

                coVerify(exactly = 2) { ollama.getModels(any<ProviderProfile>()) }
                coVerify(exactly = 0) { openAi.getModels() }
            } finally {
                AppConfig.instance.llmProvider = originalProvider
            }
        }

    @Test
    fun `request provider override routes independently from session provider`() =
        runTest {
            val originalProvider = AppConfig.instance.llmProvider
            val originalModel = AppConfig.instance.openAiModel
            try {
                AppConfig.instance.llmProvider = "ollama"
                AppConfig.instance.openAiModel = "gpt-session"
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
                AppConfig.instance.llmProvider = originalProvider
                AppConfig.instance.openAiModel = originalModel
            }
        }

    @Test
    fun `openai profile supports discovery details and streaming`() =
        runTest {
            val originalProvider = AppConfig.instance.llmProvider
            try {
                AppConfig.instance.llmProvider = "openai"
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
                AppConfig.instance.llmProvider = originalProvider
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
        )
}
