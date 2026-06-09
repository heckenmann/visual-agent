package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.config.AppConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
                val router = ConfiguredLLMProvider(ollama, openAi)

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
                coEvery { ollama.getModels() } returns listOf("llama")
                coEvery { ollama.stream(any<ChatRequestContext>()) } returns
                    flowOf(ChatResponse("llama", Message("assistant", "chunk"), true))
                val router = ConfiguredLLMProvider(ollama, openAi)

                assertEquals(true, router.isConnected())
                assertEquals(true, router.checkConnection())
                assertEquals(listOf("llama"), router.getModels())

                coVerify(exactly = 1) { ollama.checkConnection() }
                coVerify(exactly = 1) { ollama.getModels() }
                coVerify(exactly = 0) { openAi.getModels() }
            } finally {
                AppConfig.instance.llmProvider = originalProvider
            }
        }
}
