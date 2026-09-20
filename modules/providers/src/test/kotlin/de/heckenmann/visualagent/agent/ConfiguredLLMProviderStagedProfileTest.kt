package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies catalog-independent routing of a transient onboarding provider profile. */
class ConfiguredLLMProviderStagedProfileTest {
    @Test
    fun `staged provider profile validates without reading or mutating the catalog`() =
        runTest {
            val catalog = mockk<ProviderCatalogService>()
            val ollama = mockk<OllamaClient>(relaxed = true)
            val openAi = mockk<OpenAiClient>()
            val requestSlot = io.mockk.slot<ChatRequestContext>()
            every { openAi.chatReactive(capture(requestSlot)) } returns
                Mono.just(ChatResponse("staged-model", Message("assistant", "READY"), true))
            val staged =
                ProviderProfile(
                    id = "staged-openai",
                    name = "Staged OpenAI",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.example",
                    apiKey = "transient-key",
                    defaultModel = "staged-model",
                )

            ConfiguredLLMProvider(ollama, openAi, catalog)
                .chatReactive(
                    ChatRequestContext(
                        messages = listOf(Message("user", "Reply with READY.")),
                        providerProfile = staged,
                    ),
                ).awaitSingle()

            assertEquals(staged, requestSlot.captured.providerProfile)
            assertEquals("staged-model", requestSlot.captured.model)
            verify(exactly = 0) { catalog.resolve(any(), any(), any(), any()) }
        }
}
