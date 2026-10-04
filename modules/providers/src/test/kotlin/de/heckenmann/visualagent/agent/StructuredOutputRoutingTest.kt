package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ResolvedModelConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ensures catalog resolution preserves the schema and native-support evidence for every adapter. */
class StructuredOutputRoutingTest {
    @Test
    fun `all routed adapters retain the request schema and model options`(): Unit =
        runTest {
            val schema = ResponseSchema("""{"type":"object"}""")
            ProviderAdapter.entries.forEach { adapter ->
                val catalog = mockk<ProviderCatalogService>()
                val ollama = mockk<OllamaClient>()
                val openAi = mockk<OpenAiClient>()
                val codex = mockk<ProfiledProviderAdapter>()
                every { codex.adapter } returns ProviderAdapter.CODEX_CLI
                val profile = ProviderProfile("provider", "Provider", adapter, "https://example.invalid", defaultModel = "model")
                val model = ProviderModelConfig("model", options = mapOf("structuredOutput.native" to "true"))
                every { catalog.resolve(any(), any(), any(), any()) } returns ResolvedModelConfig(profile, model, null, model.options)
                val captured = slot<ChatRequestContext>()
                val selected: LLMProvider =
                    when (adapter) {
                        ProviderAdapter.OLLAMA -> ollama
                        ProviderAdapter.OPENAI_COMPATIBLE -> openAi
                        ProviderAdapter.CODEX_CLI -> codex
                    }
                every { selected.chatReactive(capture(captured)) } returns
                    Mono.just(ChatResponse("model", Message("assistant", "{}"), true))
                val details = Mono.just(ShowResponse(model = "model", modifiedAt = "", details = ModelDetails()))
                when (adapter) {
                    ProviderAdapter.OLLAMA -> every { ollama.getModelDetailsReactive(profile, "model") } returns details
                    ProviderAdapter.OPENAI_COMPATIBLE -> every { openAi.getModelDetailsReactive(profile, "model") } returns details
                    ProviderAdapter.CODEX_CLI -> every { codex.getModelDetailsReactive(profile, "model") } returns details
                }
                ConfiguredLLMProvider(ollama, openAi, catalog, profiledAdapters = listOf(codex))
                    .chatReactive(ChatRequestContext(listOf(Message("user", "review")), responseSchema = schema))
                    .awaitSingle()
                assertEquals(schema, captured.captured.responseSchema)
                assertEquals(schema, captured.captured.nativeResponseSchema())
                assertEquals("model", captured.captured.model)
            }
        }
}
