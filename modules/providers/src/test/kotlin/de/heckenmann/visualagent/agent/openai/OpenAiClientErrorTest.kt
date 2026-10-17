package de.heckenmann.visualagent.agent.openai

import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.TestProviderRuntimeConfig
import de.heckenmann.visualagent.agent.TestToolRegistry
import kotlinx.coroutines.reactor.awaitSingle
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenAiClientErrorTest {
    @Test
    fun `isConnected requires non blank api key`() {
        val config = TestProviderRuntimeConfig()
        config.openAiApiKey = ""
        config.openAiBaseUrl = "https://api.openai.com"
        val client =
            OpenAiClient(
                mockFactory(),
                TestToolRegistry(),
                config,
            )

        assertEquals(false, client.isConnected())
    }

    @Test
    fun `getModels throws when api key is blank for official endpoint`() =
        kotlinx.coroutines.test.runTest {
            val config = TestProviderRuntimeConfig()
            config.openAiApiKey = ""
            config.openAiBaseUrl = "https://api.openai.com"
            val client =
                OpenAiClient(
                    mockFactory(),
                    TestToolRegistry(),
                    config,
                )

            val error = assertFailsWith<IllegalStateException> { client.getModelsReactive().awaitSingle() }

            assertEquals("OpenAI API key is not configured", error.message)
        }

    @Test
    fun `getModelDetails returns profile family`() =
        kotlinx.coroutines.test.runTest {
            val client =
                OpenAiClient(
                    mockFactory(),
                    TestToolRegistry(),
                    TestProviderRuntimeConfig(),
                )
            val profile =
                de.heckenmann.visualagent.agent.provider.ProviderProfile(
                    id = "custom",
                    name = "Custom",
                    adapter = de.heckenmann.visualagent.agent.provider.ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "http://localhost:8080",
                    apiKey = "",
                    defaultModel = "m",
                )

            val details: ShowResponse = client.getModelDetailsReactive(profile, "m").awaitSingle()

            assertEquals("m", details.model)
            assertEquals("Custom", details.details?.family)
        }

    @Test
    fun `provider error mapper extracts response body`() {
        val exception = ExceptionWithResponseBody("HTTP 500 Internal Server Error", "{\"error\":\"bad request\"}")

        val error = OpenAiProviderErrors.detailed(exception)

        assertEquals("HTTP 500 Internal Server Error: {\"error\":\"bad request\"}", error.message)
        assertTrue(error.cause === exception)
    }

    @Test
    fun `provider error mapper preserves fallback message chain`() {
        val nested = IllegalStateException("inner")
        val exception = RuntimeException("outer", nested)

        val error = OpenAiProviderErrors.detailed(exception)

        assertEquals("inner", error.message)
    }

    private fun mockFactory(): OpenAiPromptFactory = io.mockk.mockk(relaxed = true)

    private class ExceptionWithResponseBody(
        message: String,
        private val body: String,
    ) : Exception(message) {
        @Suppress("unused")
        fun getResponseBodyAsString(): String = body
    }
}
