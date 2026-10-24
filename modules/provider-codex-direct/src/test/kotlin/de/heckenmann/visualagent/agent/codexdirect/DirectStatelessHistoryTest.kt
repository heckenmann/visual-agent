package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ProviderReplayCodec
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.metrics.ResponsesExecutionMetrics
import de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.part.OpaquePayload
import org.springframework.ai.chat.messages.part.ReasoningPart
import org.springframework.ai.chat.messages.part.TextPart
import org.springframework.ai.openai.responses.OpenAiResponsesMetadata
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Captures real SDK requests to prove lossless manual replay without provider-side conversation state. */
class DirectStatelessHistoryTest {
    @ParameterizedTest
    @EnumSource(DirectProviderAuthMode::class)
    fun `both routes resend role preserving history and encrypted native output`(mode: DirectProviderAuthMode) {
        val metrics = SimpleMeterRegistry()
        try {
            val frames =
                Flux.just(
                    DirectSseFrames.created(),
                    DirectSseFrames.delta("Next "),
                    DirectSseFrames.delta("answer"),
                    DirectSseFrames.completed("Next answer", cachedTokens = 4),
                )
            DirectSseFixture(frames).use { fixture ->
                val profile =
                    ProviderProfile(
                        "direct",
                        "Direct",
                        ProviderAdapter.CODEX_DIRECT,
                        "https://api.openai.com",
                        directAuthMode = mode,
                    )
                val tools = mockk<ProviderToolCallbacks>()
                every { tools.functionCallbacks(any(), any()) } returns emptyList()
                val credentials = mockk<DirectProviderCredentials>()
                every { credentials.bearer(profile) } returns Mono.just("synthetic")
                val models = mockk<DirectResponsesModels>()
                every { models.open("synthetic", "selected") } answers {
                    DirectResponsesModelLease(fixture.model, mockk(), mockk(), {})
                }
                val provider =
                    CodexDirectProvider(
                        mockk(),
                        credentials,
                        models,
                        OpenAiPromptFactory(tools),
                        ReactiveResponsesToolLoop(tools, metrics = ResponsesExecutionMetrics(metrics)),
                        WebClient.create(),
                    )
                val output =
                    AssistantMessage
                        .builder()
                        .parts(
                            listOf(
                                ReasoningPart(
                                    null,
                                    null,
                                    OpaquePayload("openai.responses", "encrypted_content", "synthetic-reasoning"),
                                    mapOf(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE to "rs_previous"),
                                ),
                                TextPart.of("Prior answer"),
                            ),
                        ).build()
                val replay = ProviderReplayCodec().encode(profile.id, "selected", output)
                val request =
                    ChatRequestContext(
                        listOf(
                            Message("system", "Stable agent instructions"),
                            Message("user", "Prior question"),
                            Message("assistant", "Prior answer", id = "prior-answer", providerReplay = replay),
                            Message("user", "Current question"),
                        ),
                        model = "selected",
                        providerProfile = profile,
                    )
                repeat(2) {
                    StepVerifier
                        .create(
                            provider.chatReactive(request),
                        ).assertNext { assertEquals("Next answer", it.message.content) }
                        .verifyComplete()
                }
                assertEquals(2, fixture.requests.size)
                assertEquals(20.0, metrics.get("visualagent.responses.input.tokens").counter().count())
                assertEquals(8.0, metrics.get("visualagent.responses.cached.input.tokens").counter().count())
                fixture.requests.forEach { body ->
                    val json = Json.parseToJsonElement(body).jsonObject
                    assertEquals("false", json.getValue("store").jsonPrimitive.content)
                    assertEquals("Stable agent instructions", json.getValue("instructions").jsonPrimitive.content)
                    assertFalse("previous_response_id" in json)
                    assertFalse("conversation" in json)
                    val input = json.getValue("input").jsonArray.map { it.jsonObject }
                    assertEquals(
                        "synthetic-reasoning",
                        input
                            .single {
                                it["type"]?.jsonPrimitive?.content == "reasoning"
                            }["encrypted_content"]
                            ?.jsonPrimitive
                            ?.content,
                    )
                    val messages = input.filter { it["role"] != null }
                    assertEquals(listOf("user", "assistant", "user"), messages.map { it.getValue("role").jsonPrimitive.content })
                    assertEquals(
                        listOf("Prior question", "Prior answer", "Current question"),
                        messages.map {
                            val content = it.getValue("content")
                            if (content is JsonArray) {
                                content
                                    .single()
                                    .jsonObject
                                    .getValue("text")
                                    .jsonPrimitive.content
                            } else {
                                content.jsonPrimitive.content
                            }
                        },
                    )
                }
            }
        } finally {
            metrics.close()
        }
    }
}
