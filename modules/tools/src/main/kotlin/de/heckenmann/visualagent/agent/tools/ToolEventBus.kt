package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolResult
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Event emitted for every tool call execution.
 *
 * @property toolId Tool identifier such as `file:read`
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
    private val listeners = CopyOnWriteArrayList<(ToolCallEvent) -> Unit>()

    /**
     * Register a listener for tool call events.
     *
     * @param listener Callback invoked for every tool call event
     * @return Handle that removes the listener when closed
     */
    fun addListener(listener: (ToolCallEvent) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * Publish one tool call event to all listeners.
     *
     * @param event Event payload to broadcast
     */
    fun publish(event: ToolCallEvent) {
        val safeEvent = event.copy(inputJson = sanitizeToolInputForEvent(event.inputJson))
        listeners.forEach { listener ->
            runCatching { listener(safeEvent) }
        }
    }
}
