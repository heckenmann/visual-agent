package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.metrics.ResponsesExecutionMetrics
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import kotlin.test.assertNull

/** Provider-reported usage is not treated as successful inference when the native terminal status fails. */
class DirectCachedUsageTest {
    @Test
    fun `failed native response does not contribute cache counters`() {
        val meters = SimpleMeterRegistry()
        try {
            DirectSseFixture(Flux.just(DirectSseFrames.failed("rate_limit_exceeded"))).use { fixture ->
                val request = ChatRequestContext(emptyList(), model = "selected")
                val options = directResponsesOptions(request, DirectProviderAuthMode.API_KEY, emptyList())
                val tools = mockk<ProviderToolCallbacks>()
                StepVerifier
                    .create(
                        ReactiveResponsesToolLoop(tools, metrics = ResponsesExecutionMetrics(meters)).stream(
                            fixture.model,
                            Prompt(listOf(UserMessage("Question")), options),
                            request,
                        ),
                    ).expectError()
                    .verify()
                assertNull(meters.find("visualagent.responses.input.tokens").counter())
                assertNull(meters.find("visualagent.responses.cached.input.tokens").counter())
            }
        } finally {
            meters.close()
        }
    }
}
