package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/**
 * Publishes persisted assistant-completion events to server-side consumers.
 *
 * Events are ordered, not replayed, and retained in a bounded buffer for temporarily slow
 * consumers. The persisted conversation remains the authoritative source of the latest state.
 */
class ConversationCompletionEventBus {
    private val logger = KotlinLogging.logger {}
    private val emissionLock = Any()
    private val eventSink = Sinks.many().multicast().onBackpressureBuffer<ConversationCompletionEvent>(EVENT_BUFFER_CAPACITY, false)

    /** Hot stream of assistant-completion events. */
    val events: Flux<ConversationCompletionEvent> = eventSink.asFlux()

    /** Publishes one completion event to all active consumers. */
    fun publish(event: ConversationCompletionEvent) {
        synchronized(emissionLock) {
            val result = eventSink.tryEmitNext(event)
            if (result != Sinks.EmitResult.OK) logger.warn { "Unable to emit conversation completion event: $result" }
        }
    }

    /** Publishes a completion using the supplied assistant entry ID. */
    fun publishCompletion(assistantEntryId: String) {
        publish(ConversationCompletionEvent(assistantEntryId, null))
    }

    /** Registers a compatibility listener and returns a handle that removes it. */
    fun addListener(listener: (ConversationCompletionEvent) -> Unit): AutoCloseable {
        val subscription =
            events.subscribe(
                { event ->
                    runCatching { listener(event) }
                        .onFailure { error -> logger.warn(error) { "Conversation completion listener failed." } }
                },
                { error -> logger.warn(error) { "Conversation completion stream terminated." } },
            )
        return AutoCloseable(subscription::dispose)
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 256
    }
}
