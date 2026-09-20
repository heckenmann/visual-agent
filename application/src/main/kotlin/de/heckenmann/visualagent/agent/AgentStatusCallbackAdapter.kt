package de.heckenmann.visualagent.agent

import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/**
 * Spring-managed adapter that replaces the static [AgentManager.Companion.globalAgentCallback].
 *
 * The UI registers a callback via [register], and [AgentManager] calls [notify] directly
 * instead of going through a static companion field.
 */
@Component
class AgentStatusCallbackAdapter {
    private val logger = KotlinLogging.logger {}
    private val emissionLock = Any()
    private var callback: ((String, String) -> Unit)? = null
    private val eventSink = Sinks.many().multicast().directBestEffort<AgentStatusEvent>()

    /**
     * Hot stream of sub-agent lifecycle messages.
     *
     * Messages are transient, not replayed, and may be dropped for slow consumers. Agent state
     * and job counts are queried separately and remain authoritative.
     */
    val events: Flux<AgentStatusEvent> = eventSink.asFlux()

    /**
     * Registers the UI callback that receives sub-agent lifecycle notifications.
     *
     * @param callback Callback invoked with agent ID and user-facing message
     */
    fun register(callback: (String, String) -> Unit) {
        this.callback = callback
    }

    /** Registers an additive listener for the transport boundary. */
    fun addListener(listener: (String, String) -> Unit): AutoCloseable {
        val subscription =
            events.subscribe(
                { event ->
                    runCatching { listener(event.agentId, event.message) }
                        .onFailure { error -> logger.warn(error) { "Agent status listener failed." } }
                },
                { error -> logger.warn(error) { "Agent status stream terminated." } },
            )
        return AutoCloseable(subscription::dispose)
    }

    /**
     * Notifies the registered callback (if any) of a sub-agent lifecycle event.
     *
     * @param agentId Sub-agent identifier
     * @param message User-facing message
     */
    fun notify(
        agentId: String,
        message: String,
    ) {
        runCatching { callback?.invoke(agentId, message) }
            .onFailure { error -> logger.warn(error) { "Agent status callback failed." } }
        synchronized(emissionLock) {
            val result = eventSink.tryEmitNext(AgentStatusEvent(agentId, message))
            if (result != Sinks.EmitResult.OK) logger.warn { "Unable to emit agent status event: $result" }
        }
    }
}

/** One user-facing sub-agent lifecycle message. */
data class AgentStatusEvent(
    val agentId: String,
    val message: String,
)
