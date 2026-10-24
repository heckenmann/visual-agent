package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.part.OpaquePayload
import org.springframework.ai.chat.messages.part.ReasoningPart
import org.springframework.ai.chat.messages.part.ToolCallPart
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.openai.responses.OpenAiResponsesException
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

class ResponsesOutputLimitTest {
    private val codec = ProviderReplayCodec()

    @Test
    fun `content is delivered immediately and accumulated parts are bounded before completion`() {
        val response = response(AssistantMessage("part"))
        val size = codec.serializedPartsSize(response.results.single().output)
        val source = Sinks.many().unicast().onBackpressureBuffer<SpringChatResponse>()

        StepVerifier
            .create(source.asFlux().withResponsePartLimit(codec, size))
            .then { source.tryEmitNext(response) }
            .expectNext(response)
            .then { source.tryEmitNext(response) }
            .expectErrorMatches { it is OpenAiResponsesException && it.code == "response_size_limit" }
            .verify()
    }

    @Test
    fun `opaque reasoning and tool arguments cannot bypass the output limit`() {
        val reasoning = ReasoningPart(null, null, OpaquePayload("openai.responses", "encrypted_content", "x".repeat(2048)), emptyMap())
        val call = ToolCallPart.of(AssistantMessage.ToolCall("call", "function", "tool_help", "x".repeat(2048)))
        val messages =
            listOf(
                AssistantMessage.builder().parts(listOf(reasoning)).build(),
                AssistantMessage.builder().parts(listOf(call)).build(),
            )
        messages.forEach { message ->
            StepVerifier
                .create(Flux.just(response(message)).withResponsePartLimit(codec, 1024))
                .expectErrorMatches { it is OpenAiResponsesException && it.code == "response_size_limit" }
                .verify()
        }
    }

    @Test
    fun `a new subscription starts with its own output budget`() {
        val response = response(AssistantMessage("part"))
        val stream = Flux.just(response).withResponsePartLimit(codec, codec.serializedPartsSize(response.results.single().output))
        repeat(2) { StepVerifier.create(stream).expectNext(response).verifyComplete() }
    }

    @Test
    fun `an exceeded limit cancels the source and does not finalize the stream`() {
        var cancelled = false
        var completed = false
        val response = response(AssistantMessage("part"))
        val source = Flux.concat(Flux.just(response), Flux.never()).doOnCancel { cancelled = true }

        StepVerifier
            .create(source.withResponsePartLimit(codec, 1).doOnComplete { completed = true })
            .expectError(OpenAiResponsesException::class.java)
            .verify()

        assertEquals(true, cancelled)
        assertEquals(false, completed)
    }

    private fun response(message: AssistantMessage) = SpringChatResponse(listOf(Generation(message)))
}
