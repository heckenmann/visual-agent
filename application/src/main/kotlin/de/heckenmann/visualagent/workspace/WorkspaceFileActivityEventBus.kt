package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/** One completed mutation of the managed workspace filesystem. */
data class WorkspaceFileActivity(
    val message: String,
    val relativePath: String? = null,
    val operation: String? = null,
    val success: Boolean = true,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val contextPolicy: ConversationContextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
)

/** Publishes managed workspace mutations for conversation persistence. */
@Component
class WorkspaceFileActivityEventBus {
    private val logger = KotlinLogging.logger {}
    private val emissionLock = Any()
    private val eventSink = Sinks.many().multicast().onBackpressureBuffer<WorkspaceFileActivity>(EVENT_BUFFER_CAPACITY, false)

    /**
     * Hot stream of completed workspace mutations.
     *
     * Mutations are ordered, not replayed, and retained in a bounded buffer for slow consumers
     * because the notification service uses them to persist conversation activity.
     */
    val events: Flux<WorkspaceFileActivity> = eventSink.asFlux()

    /** Registers a listener for future managed workspace mutations. */
    fun addListener(listener: (WorkspaceFileActivity) -> Unit): AutoCloseable {
        val subscription =
            events.subscribe(
                { activity ->
                    runCatching { listener(activity) }
                        .onFailure { error -> logger.warn(error) { "Workspace file activity listener failed." } }
                },
                { error -> logger.warn(error) { "Workspace file activity stream terminated." } },
            )
        return AutoCloseable(subscription::dispose)
    }

    /** Publishes one completed managed workspace mutation. */
    fun publish(activity: WorkspaceFileActivity) {
        synchronized(emissionLock) {
            val result = eventSink.tryEmitNext(activity)
            if (result != Sinks.EmitResult.OK) logger.warn { "Unable to emit workspace file activity: $result" }
        }
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 256
    }
}
