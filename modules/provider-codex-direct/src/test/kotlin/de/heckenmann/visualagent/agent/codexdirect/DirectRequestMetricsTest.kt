package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.Message
import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class DirectRequestMetricsTest {
    @Test
    fun `stage records a failure once per subscription without changing the error`() {
        val clock = MockClock()
        val registry = SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)
        try {
            val failure = IllegalStateException("synthetic")
            val source =
                Mono.defer<String> {
                    clock.add(Duration.ofMillis(5))
                    Mono.error(failure)
                }
            val stage = DirectRequestMetrics(registry).stage(source, DirectRequestStage.CREDENTIAL)
            repeat(2) {
                StepVerifier.create(stage).expectErrorMatches { it === failure }.verify()
            }
            val timer =
                registry
                    .get("visualagent.codex.direct.stage")
                    .tag("stage", "CREDENTIAL")
                    .tag("termination", "ON_ERROR")
                    .timer()
            assertEquals(2L, timer.count())
            assertEquals(10.0, timer.totalTime(TimeUnit.MILLISECONDS))
        } finally {
            registry.close()
        }
    }

    @Test
    fun `first visible text excludes empty events and measurements are subscription scoped`() {
        val clock = MockClock()
        val registry = SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)
        try {
            val source =
                Flux.defer {
                    Flux.just("", "text", "more").map {
                        clock.add(Duration.ofMillis(10))
                        ChatResponse("model", Message("assistant", it), false)
                    }
                }
            val measured = DirectRequestMetrics(registry).observe(source)
            repeat(2) {
                StepVerifier.create(measured).expectNextCount(3).verifyComplete()
            }
            val first = registry.get("visualagent.codex.direct.first.text").timer()
            assertEquals(2L, first.count())
            assertEquals(40.0, first.totalTime(TimeUnit.MILLISECONDS))
            val total = registry.get("visualagent.codex.direct.request").tag("termination", "ON_COMPLETE").timer()
            assertEquals(2L, total.count())
            assertEquals(60.0, total.totalTime(TimeUnit.MILLISECONDS))
        } finally {
            registry.close()
        }
    }

    @Test
    fun `cancellation records termination without fabricating a first text event`() {
        val registry = SimpleMeterRegistry()
        try {
            StepVerifier.create(DirectRequestMetrics(registry).observe(Flux.never())).thenCancel().verify()
            assertEquals(
                1L,
                registry
                    .get("visualagent.codex.direct.request")
                    .tag("termination", "CANCEL")
                    .timer()
                    .count(),
            )
            assertEquals(null, registry.find("visualagent.codex.direct.first.text").timer())
        } finally {
            registry.close()
        }
    }
}
