package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.protocol.DownloadActivity
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/** Publishes lifecycle transitions for server-owned workspace downloads. */
@Component
class WorkspaceDownloadEventBus {
    private val logger = KotlinLogging.logger {}
    private val emissionLock = Any()
    private val eventSink = Sinks.many().multicast().onBackpressureBuffer<DownloadActivity>(EVENT_BUFFER_CAPACITY, false)

    /**
     * Hot stream of ordered download status transitions.
     *
     * Transitions are not replayed and use a bounded buffer so terminal states are not silently
     * lost for temporarily slow consumers. The active-download query remains authoritative.
     */
    val events: Flux<DownloadActivity> = eventSink.asFlux()

    /** Registers a listener for future download status transitions. */
    fun addListener(listener: (DownloadActivity) -> Unit): AutoCloseable {
        val subscription =
            events.subscribe(
                { event ->
                    runCatching { listener(event) }
                        .onFailure { error -> logger.warn(error) { "Workspace download listener failed." } }
                },
                { error -> logger.warn(error) { "Workspace download stream terminated." } },
            )
        return AutoCloseable(subscription::dispose)
    }

    /** Publishes one download status transition to all listeners. */
    fun publish(event: DownloadActivity) {
        synchronized(emissionLock) {
            val result = eventSink.tryEmitNext(event)
            if (result != Sinks.EmitResult.OK) logger.warn { "Unable to emit workspace download event: $result" }
        }
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 256
    }
}
