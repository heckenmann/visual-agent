package de.heckenmann.visualagent.agent.openai

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAiModelFilterTest {
    @Test
    fun `official endpoint keeps chat and reasoning models only`() {
        val models =
            OpenAiModelFilter.filter(
                modelIds =
                    listOf(
                        "gpt-5",
                        "gpt-4o-mini",
                        "o3",
                        "o4-mini",
                        "chatgpt-4o-latest",
                        "ft:gpt-4o-mini:team:custom:id",
                        "text-embedding-3-small",
                        "gpt-image-1",
                        "gpt-4o-realtime-preview",
                        "gpt-4o-audio-preview",
                        "whisper-1",
                        "tts-1",
                        "omni-moderation-latest",
                        "davinci-002",
                    ),
                modelsUri = URI.create("https://api.openai.com/v1/models"),
            )

        assertEquals(
            listOf(
                "chatgpt-4o-latest",
                "ft:gpt-4o-mini:team:custom:id",
                "gpt-4o-mini",
                "gpt-5",
                "o3",
                "o4-mini",
            ),
            models,
        )
    }

    @Test
    fun `compatible endpoint preserves unknown text model families`() {
        val models =
            OpenAiModelFilter.filter(
                modelIds = listOf("mistral-large", "llama-3.3", "provider-embedding-model"),
                modelsUri = URI.create("https://llm.example.com/v1/models"),
            )

        assertEquals(listOf("llama-3.3", "mistral-large"), models)
    }
}
