package de.heckenmann.visualagent.agent.openai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
