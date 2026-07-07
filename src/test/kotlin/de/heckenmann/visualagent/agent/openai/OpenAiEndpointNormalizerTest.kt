package de.heckenmann.visualagent.agent.openai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAiEndpointNormalizerTest {
    @Test
    fun `official endpoint receives v1 API path`() {
        assertEquals(
            "https://api.openai.com/v1",
            OpenAiEndpointNormalizer.apiBaseUrl("https://api.openai.com"),
        )
    }

    @Test
    fun `existing v1 path is preserved`() {
        assertEquals(
            "https://example.test/openai/v1",
            OpenAiEndpointNormalizer.apiBaseUrl("https://example.test/openai/v1/"),
        )
    }

    @Test
    fun `compatible endpoint path receives v1 suffix`() {
        assertEquals(
            "http://localhost:8080/openai/v1",
            OpenAiEndpointNormalizer.apiBaseUrl("http://localhost:8080/openai"),
        )
    }

    @Test
    fun `blank url falls back to official endpoint`() {
        assertEquals("https://api.openai.com/v1", OpenAiEndpointNormalizer.apiBaseUrl(""))
    }

    @Test
    fun `trailing slash is removed`() {
        assertEquals(
            "https://api.openai.com/v1",
            OpenAiEndpointNormalizer.apiBaseUrl("https://api.openai.com/"),
        )
    }

    @Test
    fun `requires api key only for official endpoint`() {
        assertTrue(OpenAiEndpointNormalizer.requiresApiKey("https://api.openai.com"))
        assertFalse(OpenAiEndpointNormalizer.requiresApiKey("http://localhost:8080/openai"))
        assertTrue(OpenAiEndpointNormalizer.requiresApiKey(""))
    }
}
