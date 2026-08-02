package de.heckenmann.visualagent.agent.openai

import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals

class OpenAiModelFilterTest {
    @Test
    fun `filter removes unsupported capability models`() {
        val models = listOf("gpt-4o", "whisper-1", "tts-1", "embedding-ada")

        val filtered = OpenAiModelFilter.filter(models, URI("http://localhost:8080/v1"))

        assertEquals(listOf("gpt-4o"), filtered)
    }

    @Test
    fun `filter keeps all non-capability models on local endpoints`() {
        val models = listOf("custom-model", "gpt-4o", "local-llm")

        val filtered = OpenAiModelFilter.filter(models, URI("http://localhost:8080/v1"))

        assertEquals(listOf("custom-model", "gpt-4o", "local-llm"), filtered)
    }

    @Test
    fun `filter on official endpoint only keeps chat models`() {
        val models = listOf("gpt-4o", "custom-model", "o1-preview", "chatgpt-4o-latest")

        val filtered = OpenAiModelFilter.filter(models, URI("https://api.openai.com/v1"))

        assertEquals(listOf("chatgpt-4o-latest", "gpt-4o", "o1-preview"), filtered)
    }

    @Test
    fun `filter removes duplicates and sorts`() {
        val models = listOf("gpt-4o", "gpt-4o", "local-llm", "gpt-3.5-turbo")

        val filtered = OpenAiModelFilter.filter(models, URI("http://localhost:8080/v1"))

        assertEquals(listOf("gpt-3.5-turbo", "gpt-4o", "local-llm"), filtered)
    }

    @Test
    fun `filter trims and drops blank ids`() {
        val models = listOf("  gpt-4o  ", "", "  ", "local-llm")

        val filtered = OpenAiModelFilter.filter(models, URI("http://localhost:8080/v1"))

        assertEquals(listOf("gpt-4o", "local-llm"), filtered)
    }
}
