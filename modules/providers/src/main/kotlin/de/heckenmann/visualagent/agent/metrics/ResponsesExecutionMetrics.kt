package de.heckenmann.visualagent.agent.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Separates model-round and registry execution latency without recording request content. */
@Component
class ResponsesExecutionMetrics(
    private val registry: MeterRegistry? = null,
) {
    /** Measures one native model stream independently of later tool execution or continuations. */
    fun model(source: Flux<ChatResponse>): Flux<ChatResponse> {
        val meters = registry ?: return source
        return Flux.defer {
            val start = meters.config().clock().monotonicTime()
            val first = AtomicBoolean()
            source
                .doOnNext { response ->
                    if (response.result
                            ?.output
                            ?.text
                            ?.isNotEmpty() == true &&
                        first.compareAndSet(false, true)
                    ) {
                        meters.timer("visualagent.responses.model.first.text").record(
                            meters.config().clock().monotonicTime() - start,
                            TimeUnit.NANOSECONDS,
                        )
                    }
                }.doFinally { signal ->
                    meters.timer("visualagent.responses.model.round", "termination", signal.name).record(
                        meters.config().clock().monotonicTime() - start,
                        TimeUnit.NANOSECONDS,
                    )
                }
        }
    }

    /** Measures one complete registry round, including waiting for its selected tools. */
    fun <T : Any> tools(source: Mono<T>): Mono<T> {
        val meters = registry ?: return source
        return Mono.defer {
            val start = meters.config().clock().monotonicTime()
            source.doFinally { signal ->
                meters.timer("visualagent.responses.tools.round", "termination", signal.name).record(
                    meters.config().clock().monotonicTime() - start,
                    TimeUnit.NANOSECONDS,
                )
            }
        }
    }
}
