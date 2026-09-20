package de.heckenmann.visualagent.todo

import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/**
 * In-process pub/sub bus for todo list mutations.
 *
 * The autonomous coordinator subscribes so that a running sub-agent can react
 * when its assigned todo is modified by the user or by the main agent.
 */
@Component
class TodoEventBus {
    private val logger = KotlinLogging.logger {}
    private val emissionLock = Any()
    private val changeSink = Sinks.many().multicast().onBackpressureBuffer<TodoChange>(EVENT_BUFFER_CAPACITY, false)
    private val progressSink = Sinks.many().multicast().directBestEffort<TodoProgressUpdate>()

    /**
     * Hot stream of persisted todo changes.
     *
     * Changes are ordered, not replayed, and retained in a bounded buffer for slow consumers.
     * The authoritative current state remains available through [TodoManager.list].
     */
    val changes: Flux<TodoChange> = changeSink.asFlux()

    /**
     * Hot stream of transient todo progress updates.
     *
     * Updates are ordered for active consumers, are not replayed, and may be dropped for slow
     * consumers because the persisted todo state is authoritative.
     */
    val progress: Flux<TodoProgressUpdate> = progressSink.asFlux()

    /**
     * Register a listener that receives all todo change events.
     *
     * @param listener Callback invoked after each state mutation
     * @return Handle that removes the listener when closed
     */
    fun addListener(listener: (TodoChange) -> Unit): AutoCloseable {
        val subscription =
            changes.subscribe(
                { change ->
                    runCatching { listener(change) }
                        .onFailure { error -> logger.warn(error) { "Todo change listener failed." } }
                },
                { error -> logger.warn(error) { "Todo change stream terminated." } },
            )
        return AutoCloseable(subscription::dispose)
    }

    /**
     * Register a listener for transient LLM output produced while a todo is processing.
     *
     * @param listener Callback invoked for each response delta and stream completion
     * @return Handle that removes the listener when closed
     */
    fun addProgressListener(listener: (TodoProgressUpdate) -> Unit): AutoCloseable {
        val subscription =
            progress.subscribe(
                { update ->
                    runCatching { listener(update) }
                        .onFailure { error -> logger.warn(error) { "Todo progress listener failed." } }
                },
                { error -> logger.warn(error) { "Todo progress stream terminated." } },
            )
        return AutoCloseable(subscription::dispose)
    }

    /**
     * Publish one todo change event to all listeners.
     *
     * @param change Event payload to broadcast
     */
    fun publish(change: TodoChange) {
        synchronized(emissionLock) {
            logEmissionFailure("todo change", changeSink.tryEmitNext(change))
        }
    }

    /**
     * Publish one transient LLM response update without changing persisted todo state.
     *
     * @param update Response delta and stream state
     */
    fun publishProgress(update: TodoProgressUpdate) {
        synchronized(emissionLock) {
            logEmissionFailure("todo progress", progressSink.tryEmitNext(update))
        }
    }

    private fun logEmissionFailure(
        eventType: String,
        result: Sinks.EmitResult,
    ) {
        if (result != Sinks.EmitResult.OK) {
            logger.warn { "Unable to emit $eventType event: $result" }
        }
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 256
    }
}

/**
 * Transient output emitted while an LLM is processing one todo.
 *
 * @property todoId Todo that owns the response
 * @property delta New response text since the previous update
 * @property completed Whether the response stream has ended
 */
data class TodoProgressUpdate(
    val todoId: String,
    val delta: String = "",
    val completed: Boolean = false,
    val executionId: String? = null,
    val agentId: String? = null,
)
