package de.heckenmann.visualagent.agent.metrics

import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class ResponsesExecutionMetricsTest {
    @Test
    fun `usage counts only explicit valid cache reports without treating unknown values as misses`() {
        val registry = SimpleMeterRegistry()
        try {
            val metrics = ResponsesExecutionMetrics(registry)

            fun report(
                input: Int?,
                cached: Long?,
            ) = metrics.usage(
                DefaultUsage(input, 0, input, null, cached, null),
            )
            report(100, null)
            report(null, 10)
            report(100, -1)
            report(100, 101)
            report(-1, 0)
            assertEquals(null, registry.find("visualagent.responses.input.tokens").counter())
            report(100, 40)
            report(50, 0)
            assertEquals(150.0, registry.get("visualagent.responses.input.tokens").counter().count())
            assertEquals(40.0, registry.get("visualagent.responses.cached.input.tokens").counter().count())
        } finally {
            registry.close()
        }
    }

    @Test
    fun `model rounds ignore empty events and tools have independent timing`() {
        val clock = MockClock()
        val registry = SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)
        try {
            val metrics = ResponsesExecutionMetrics(registry)
            val source =
                Flux.just("", "text", "more").map {
                    clock.add(Duration.ofMillis(10))
                    ChatResponse(listOf(Generation(AssistantMessage(it))))
                }
            repeat(2) { StepVerifier.create(metrics.model(source)).expectNextCount(3).verifyComplete() }
            StepVerifier
                .create(
                    metrics.tools(
                        Mono.fromSupplier {
                            clock.add(Duration.ofMillis(50))
                            "tool result"
                        },
                    ),
                ).expectNext("tool result")
                .verifyComplete()
            assertEquals(40.0, registry.get("visualagent.responses.model.first.text").timer().totalTime(TimeUnit.MILLISECONDS))
            val rounds = registry.get("visualagent.responses.model.round").tag("termination", "ON_COMPLETE").timer()
            assertEquals(2L, rounds.count())
            assertEquals(60.0, rounds.totalTime(TimeUnit.MILLISECONDS))
            assertEquals(50.0, registry.get("visualagent.responses.tools.round").timer().totalTime(TimeUnit.MILLISECONDS))
        } finally {
            registry.close()
        }
    }

    @Test
    fun `cancelling a model round cancels its source and does not emit first text`() {
        val registry = SimpleMeterRegistry()
        try {
            var cancelled = false
            StepVerifier
                .create(
                    ResponsesExecutionMetrics(registry).model(Flux.never<ChatResponse>().doOnCancel { cancelled = true }),
                ).thenCancel()
                .verify()
            assertEquals(true, cancelled)
            assertEquals(
                1L,
                registry
                    .get("visualagent.responses.model.round")
                    .tag("termination", "CANCEL")
                    .timer()
                    .count(),
            )
            assertEquals(null, registry.find("visualagent.responses.model.first.text").timer())
        } finally {
            registry.close()
        }
    }
}
