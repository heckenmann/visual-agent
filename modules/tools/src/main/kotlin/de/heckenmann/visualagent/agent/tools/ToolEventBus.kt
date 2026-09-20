package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolResult
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Instant

/**
 * Event emitted for every tool call execution.
 *
 * @property toolId Tool identifier such as `workspace:file`
 * @property functionName Provider-safe function callback name
 * @property providerToolCallId Provider request-scoped call identity
 * @property requestId Visual Agent request identity
 * @property round Zero-based tool-loop round
 * @property sequence Provider call position within its round
 * @property inputJson Sanitized JSON input passed by the model
 * @property context Request-scoped context attached to the tool callback
 * @property result Structured tool execution result
 * @property startedAtUtc Start time in UTC
 * @property finishedAtUtc Finish time in UTC
 * @property durationMillis Wall-clock duration in milliseconds
 */
data class ToolCallEvent(
    val toolId: String,
    val functionName: String,
    val providerToolCallId: String? = null,
    val requestId: String? = null,
    val round: Int? = null,
    val sequence: Int? = null,
    val phase: ToolCallPhase = ToolCallPhase.FINISHED,
    val inputJson: String,
    val context: Map<String, Any>,
    val result: ToolResult,
    val startedAtUtc: Instant,
    val finishedAtUtc: Instant,
    val durationMillis: Long,
)

/**
 * Lifecycle phase of one tool call.
 */
enum class ToolCallPhase {
    STARTED,
    FINISHED,
}

/**
 * In-process pub/sub bus for tool execution lifecycle events.
 */
@Component
class ToolEventBus {
    private val logger = KotlinLogging.logger {}
    private val emissionLock = Any()
    private val eventSink = Sinks.many().multicast().onBackpressureBuffer<ToolCallEvent>(EVENT_BUFFER_CAPACITY, false)

    /**
     * Hot stream of sanitized lifecycle events for active tool executions.
     *
     * Events are ordered, not replayed, and retained in a bounded buffer of 256 entries. STARTED
     * and FINISHED events are therefore preserved for temporarily slow consumers; an overflow is
     * logged by [publish]. Consumers should keep their work non-blocking and apply their own
     * scheduling when needed.
     */
    val events: Flux<ToolCallEvent> = eventSink.asFlux()

    /**
     * Register a listener for tool call events.
     *
     * @param listener Callback invoked for every tool call event
     * @return Handle that removes the listener when closed
     */
    fun addListener(listener: (ToolCallEvent) -> Unit): AutoCloseable {
        val subscription =
            events.subscribe { event ->
                runCatching { listener(event) }
                    .onFailure { error -> logger.warn(error) { "Tool lifecycle listener failed." } }
            }
        return AutoCloseable(subscription::dispose)
    }

    /**
     * Publish one tool call event to all listeners.
     *
     * @param event Event payload to broadcast
     */
    fun publish(event: ToolCallEvent) {
        val safeEvent = event.copy(inputJson = sanitizeToolInputForEvent(event.inputJson, event.toolId))
        synchronized(emissionLock) {
            val emission = eventSink.tryEmitNext(safeEvent)
            if (emission != Sinks.EmitResult.OK) {
                logger.warn { "Tool lifecycle event was not delivered: $emission" }
            }
        }
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 256
    }
}
