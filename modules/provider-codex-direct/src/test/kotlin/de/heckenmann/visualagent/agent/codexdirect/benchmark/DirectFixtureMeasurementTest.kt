package de.heckenmann.visualagent.agent.codexdirect.benchmark

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.codexdirect.DirectSseFixture
import de.heckenmann.visualagent.agent.codexdirect.DirectSseFrames
import de.heckenmann.visualagent.agent.codexdirect.directResponsesOptions
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import kotlin.test.assertEquals

/** Verifies measurement counters against actual local SDK requests, never elapsed-time thresholds. */
class DirectFixtureMeasurementTest {
    @Test
    fun `reported cached tokens survive real SDK and Spring AI response mapping`() {
        DirectSseFixture(Flux.just(DirectSseFrames.completed("answer", cachedTokens = 7))).use { fixture ->
            val request = ChatRequestContext(emptyList(), model = "selected")
            val prompt =
                Prompt(listOf(UserMessage("question")), directResponsesOptions(request, DirectProviderAuthMode.API_KEY, emptyList()))
            StepVerifier
                .create(ReactiveResponsesToolLoop(mockk()).stream(fixture.model, prompt, request))
                .assertNext { assertEquals(7L, it.providerTurn?.usage?.cachedPromptTokens) }
                .verifyComplete()
        }
    }

    @Test
    fun `sequential streams count one accepted channel and exact UTF-8 payloads`() {
        val frame = DirectSseFrames.completed("synthetic answer")
        DirectSseFixture(Flux.just(frame)).use { fixture ->
            val model = fixture.scopedModel("synthetic")
            StepVerifier
                .create(
                    Flux.range(0, 3).concatMap { model.stream(Prompt(listOf(UserMessage("synthetic question")))) },
                ).expectNextCount(3)
                .verifyComplete()

            assertEquals(3, fixture.requestCount.get())
            assertEquals(1, fixture.connections.get())
            assertEquals(fixture.requests.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }, fixture.receivedBytes.get())
            assertEquals(3L * frame.toByteArray(Charsets.UTF_8).size, fixture.sentBytes.get())
        }
    }

    @Test
    fun `disabling body capture retains request and payload counters`() {
        DirectSseFixture({ Flux.just(DirectSseFrames.completed("answer")) }, captureBodies = false).use { fixture ->
            StepVerifier
                .create(fixture.model.stream(Prompt(listOf(UserMessage("question")))))
                .expectNextCount(1)
                .verifyComplete()
            assertEquals(0, fixture.requests.size)
            assertEquals(1, fixture.requestCount.get())
            assertEquals(1, fixture.connections.get())
        }
    }
}
