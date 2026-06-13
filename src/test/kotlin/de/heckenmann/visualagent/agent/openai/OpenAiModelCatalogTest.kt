package de.heckenmann.visualagent.agent.openai

import com.openai.client.OpenAIClient
import com.openai.models.models.Model
import com.openai.models.models.ModelListPage
import com.openai.services.blocking.ModelService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI

class OpenAiModelCatalogTest {
    @Test
    fun `load returns filtered chat models and closes client`() {
        val client = mockk<OpenAIClient>(relaxed = true)
        val modelService = mockk<ModelService>()
        val page = mockk<ModelListPage>()
        val models =
            listOf(
                model("gpt-5"),
                model("text-embedding-3-small"),
                model("o4-mini"),
            )
        every { client.models() } returns modelService
        every { modelService.list() } returns page
        every { page.data() } returns models

        val result =
            OpenAiModelCatalog { client }.load(
                URI.create("https://api.openai.com/v1/models"),
            )

        assertEquals(listOf("gpt-5", "o4-mini"), result)
        verify(exactly = 1) { client.close() }
    }

    @Test
    fun `load propagates provider error and still closes client`() {
        val client = mockk<OpenAIClient>(relaxed = true)
        val modelService = mockk<ModelService>()
        val providerError = IllegalStateException("Unauthorized")
        every { client.models() } returns modelService
        every { modelService.list() } throws providerError

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                OpenAiModelCatalog { client }.load(
                    URI.create("https://api.openai.com/v1/models"),
                )
            }

        assertEquals(providerError, thrown)
        verify(exactly = 1) { client.close() }
    }

    private fun model(id: String): Model =
        mockk {
            every { this@mockk.id() } returns id
        }
}
