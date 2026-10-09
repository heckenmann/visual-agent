package de.heckenmann.visualagent.orchestration

import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/** Conflates state changes that may make autonomous work executable. */
internal class AutonomousWorkSignal {
    private val signals = Sinks.many().replay().latest<Long>()
    private var sequence = 0L

    /** Serializes emissions and retains the latest signal until pickup subscribes. */
    @Synchronized
    fun signal() {
        signals.tryEmitNext(++sequence)
    }

    /** Supplies pickup requests with one pending value under backpressure. */
    fun events(): Flux<Long> = signals.asFlux().onBackpressureLatest()
}
