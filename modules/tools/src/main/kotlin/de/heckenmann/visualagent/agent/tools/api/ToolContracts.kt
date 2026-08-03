package de.heckenmann.visualagent.agent.tools.api

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stable identifier for a model-callable tool.
 *
 * @property value External identifier such as `file:read` or `ui`
 */
@JvmInline
value class ToolId(
    val value: String,
)

/**
 * Provider-neutral description of a callable tool.
 *
 * @property id Stable tool identifier
 * @property name Provider-safe function name
 * @property description Description presented to the model
 * @property inputSchema JSON schema for tool input
 */
data class ToolDefinition(
    val id: ToolId,
    val name: String,
    val description: String,
    val inputSchema: String,
)

/**
 * Structured result returned by a tool execution.
 *
 * @property toolId Tool that produced the result
 * @property success Whether execution succeeded
 * @property content Human-readable result payload
 * @property error Optional error detail for failed execution
 */
@Serializable
data class ToolResult(
    val toolId: String,
    val success: Boolean,
    val content: String,
    val error: String? = null,
)

/**
 * Lifecycle event emitted around one tool call.
 *
 * @property toolId Stable tool identifier
 * @property functionName Provider-facing callback name
 * @property phase Lifecycle phase
 * @property inputJson Raw model input
 * @property context Request-scoped execution metadata
 * @property result Current structured result
 * @property startedAtUtc Start timestamp
 * @property finishedAtUtc Finish timestamp
 * @property durationMillis Elapsed wall-clock time
 */
data class ToolCallEvent(
    val toolId: String,
    val functionName: String,
    val phase: ToolCallPhase,
    val inputJson: String,
    val context: Map<String, Any>,
    val result: ToolResult,
    val startedAtUtc: Instant,
    val finishedAtUtc: Instant,
    val durationMillis: Long,
)

/**
 * Lifecycle phase of a tool call.
 */
enum class ToolCallPhase {
    STARTED,
    FINISHED,
}

/**
 * In-process publisher for tool lifecycle events.
 *
 * The application supplies the Spring bean and connects listeners during composition.
 */
class ToolEventBus {
    private val listeners = CopyOnWriteArrayList<(ToolCallEvent) -> Unit>()

    /**
     * Register a listener for future events.
     *
     * @param listener Callback invoked for each event
     * @return Handle that removes the listener when closed
     */
    fun addListener(listener: (ToolCallEvent) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * Publish an event to all registered listeners.
     *
     * @param event Event to publish
     */
    fun publish(event: ToolCallEvent) {
        listeners.forEach { listener ->
            runCatching { listener(event) }
        }
    }
}
