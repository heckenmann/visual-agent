package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatResponse
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Measures request latency without retaining prompts, credentials, or profile identifiers. */
@Component
internal class DirectRequestMetrics(
    private val registry: MeterRegistry? = null,
) {
    /** Measures setup stages without labels derived from user or provider data. */
    fun <T : Any> stage(
        source: Mono<T>,
        stage: DirectRequestStage,
    ): Mono<T> {
        val meters = registry ?: return source
        return Mono.defer {
            val started = meters.config().clock().monotonicTime()
            source.doFinally { signal ->
                meters.timer("visualagent.codex.direct.stage", "stage", stage.name, "termination", signal.name).record(
                    meters.config().clock().monotonicTime() - started,
                    TimeUnit.NANOSECONDS,
                )
            }
        }
    }

    /** Starts a separate measurement for each subscription, including setup and tool continuations. */
    fun observe(source: Flux<ChatResponse>): Flux<ChatResponse> {
        val meters = registry ?: return source
        return Flux.defer {
            val started = meters.config().clock().monotonicTime()
            val firstText = AtomicBoolean()
            source
                .doOnNext { chunk ->
                    if (chunk.message.content.isNotEmpty() && firstText.compareAndSet(false, true)) {
                        meters.timer("visualagent.codex.direct.first.text").record(
                            meters.config().clock().monotonicTime() - started,
                            TimeUnit.NANOSECONDS,
                        )
                    }
                }.doFinally { signal ->
                    meters.timer("visualagent.codex.direct.request", "termination", signal.name).record(
                        meters.config().clock().monotonicTime() - started,
                        TimeUnit.NANOSECONDS,
                    )
                }
        }
    }
}

/** Bounded stage names shared by direct-provider latency measurements. */
internal enum class DirectRequestStage {
    /** Reads or refreshes the credential selected for this request. */
    CREDENTIAL,

    /** Acquires SDK and TLS resources for the request. */
    TRANSPORT,

    /** Validates and encodes a native attachment once for this request. */
    ATTACHMENT_PREPARATION,

    /** Counts native attachment input before inference. */
    ATTACHMENT_COUNT,
}
