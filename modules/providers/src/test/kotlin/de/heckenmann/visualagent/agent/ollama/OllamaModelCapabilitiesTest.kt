package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.ollama.api.OllamaApi
import kotlin.test.assertEquals

/** Covers complete, missing, and failed Ollama capability declarations. */
class OllamaModelCapabilitiesTest {
    @Test
    fun `reads every reported capability from show rather than tags`() {
        val api = apiWithModels("full", "empty", "failed")
        val result = fetchModelCapabilitiesReactive(profile(), api).block()

        assertEquals(mapOf("full" to setOf("completion", "tools", "vision", "thinking", "embedding")), result)
    }

    private fun apiWithModels(vararg names: String): OllamaApi {
        val api = mockk<OllamaApi>()
        val response = mockk<OllamaApi.ListModelResponse>()
        every { response.models() } returns
            names.map { name ->
                mockk<OllamaApi.Model>().also { model -> every { model.name() } returns name }
            }
        every { api.listModels() } returns response
        val full = mockk<OllamaApi.ShowModelResponse>()
        val empty = mockk<OllamaApi.ShowModelResponse>()
        every { full.capabilities() } returns listOf("completion", "tools", "vision", "thinking", "embedding")
        every { empty.capabilities() } returns emptyList()
        every { api.showModel(any()) } answers {
            when (firstArg<OllamaApi.ShowModelRequest>().model()) {
                "full" -> full
                "empty" -> empty
                else -> throw IllegalStateException("unavailable")
            }
        }
        return api
    }

    private fun profile() =
        ProviderProfile(
            id = "ollama",
            name = "Ollama",
            adapter = ProviderAdapter.OLLAMA,
            baseUrl = "http://localhost:11434",
        )
}
