package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingException
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DirectSseIntegrationTest {
    @Test
    fun `real SSE transport delivers each delta before the terminal event and retains usage`() {
        val frames = Sinks.many().unicast().onBackpressureBuffer<String>()
        DirectSseFixture(frames.asFlux()).use { fixture ->
            StepVerifier
                .create(stream(fixture))
                .then {
                    frames.tryEmitNext(DirectSseFrames.created())
                    frames.tryEmitNext(DirectSseFrames.delta("first"))
                }.assertNext { assertEquals("first", it.message.content) }
                .then { frames.tryEmitNext(DirectSseFrames.delta("second")) }
                .assertNext { assertEquals("second", it.message.content) }
                .then {
                    frames.tryEmitNext(DirectSseFrames.completed("firstsecond"))
                    frames.tryEmitComplete()
                }.assertNext { assertEquals(12, it.providerTurn?.usage?.totalTokens) }
                .verifyComplete()
            assertEquals(1, fixture.requests.size)
        }
    }

    @Test
    fun `clean HTTP EOF without completed event is not successful inference`() {
        DirectSseFixture(Flux.just(DirectSseFrames.created(), DirectSseFrames.delta("partial"))).use { fixture ->
            StepVerifier
                .create(stream(fixture))
                .assertNext { assertEquals("partial", it.message.content) }
                .expectErrorMatches { it is ProviderUserFacingException && it.userFacing.summary == "Incomplete provider response" }
                .verify()
        }
    }

    @Test
    fun `malformed SSE fails safely without exposing raw provider data`() {
        DirectSseFixture(Flux.just("event: response.output_text.delta\ndata: {malformed private payload}\n\n")).use { fixture ->
            StepVerifier
                .create(stream(fixture))
                .expectErrorMatches {
                    assertFalse(it.toString().contains("private payload"))
                    it is ProviderUserFacingException && it.userFacing.summary == "Invalid provider stream"
                }.verify()
        }
    }

    @Test
    fun `plan errors after partial output retain their structured recovery classification`() {
        DirectSseFixture(
            Flux.just(
                DirectSseFrames.created(),
                DirectSseFrames.delta("partial"),
                DirectSseFrames.failed("subscription_sharing_usage_limit_exceeded"),
            ),
        ).use { fixture ->
            StepVerifier
                .create(stream(fixture))
                .assertNext { assertEquals("partial", it.message.content) }
                .expectErrorMatches { it is ProviderUserFacingException && it.userFacing.summary == "ChatGPT plan usage limit" }
                .verify()
        }
    }

    @Test
    fun `cancelling an active stream closes the actual HTTP connection`() {
        val frames = Sinks.many().unicast().onBackpressureBuffer<String>()
        DirectSseFixture(frames.asFlux()).use { fixture ->
            StepVerifier
                .create(stream(fixture))
                .then { frames.tryEmitNext(DirectSseFrames.delta("first")) }
                .assertNext { assertEquals("first", it.message.content) }
                .thenCancel()
                .verify()
            StepVerifier.create(fixture.disconnected.asMono()).verifyComplete()
        }
    }

    private fun stream(fixture: DirectSseFixture): Flux<de.heckenmann.visualagent.agent.ChatResponse> {
        val request = ChatRequestContext(emptyList(), model = "selected")
        val options = directResponsesOptions(request, DirectProviderAuthMode.CHATGPT_OAUTH, emptyList())
        return ReactiveResponsesToolLoop(mockk())
            .stream(
                DirectResponsesTransport(fixture.model, DirectProviderAuthMode.CHATGPT_OAUTH),
                Prompt(listOf(UserMessage("question")), options),
                request,
            ).onErrorMap(DirectProviderErrors::safe)
    }
}
