package de.heckenmann.visualagent.agent.openai

import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAiClientCredentialTest {
    @Test
    fun `official openai endpoint requires an api key before model discovery`() =
        runTest {
            val client = OpenAiClient(mockk(relaxed = true))
            val profile =
                ProviderProfile(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.openai.com",
                    apiKey = "",
                    defaultModel = "gpt-4.1-mini",
                )

            val error = assertFailsWith<IllegalStateException> { client.getModels(profile) }

            assertEquals("OpenAI API key is not configured", error.message)
        }

    @Test
    fun `only official openai endpoint requires credentials`() {
        assertTrue(OpenAiEndpointNormalizer.requiresApiKey("https://api.openai.com"))
        assertTrue(OpenAiEndpointNormalizer.requiresApiKey(""))
        assertFalse(OpenAiEndpointNormalizer.requiresApiKey("http://localhost:8080/v1"))
        assertFalse(OpenAiEndpointNormalizer.requiresApiKey("https://llm-gateway.example.test/openai"))
    }
}
