package de.heckenmann.visualagent.todo

import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process pub/sub bus for todo list mutations.
 *
 * The autonomous coordinator subscribes so that a running sub-agent can react
 * when its assigned todo is modified by the user or by the main agent.
 */
@Component
class TodoEventBus {
    private val listeners = CopyOnWriteArrayList<(TodoChange) -> Unit>()
    private val progressListeners = CopyOnWriteArrayList<(TodoProgressUpdate) -> Unit>()

    /**
     * Register a listener that receives all todo change events.
     *
     * @param listener Callback invoked after each state mutation
     * @return Handle that removes the listener when closed
     */
    fun addListener(listener: (TodoChange) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * Register a listener for transient LLM output produced while a todo is processing.
     *
     * @param listener Callback invoked for each response delta and stream completion
     * @return Handle that removes the listener when closed
     */
    fun addProgressListener(listener: (TodoProgressUpdate) -> Unit): AutoCloseable {
        progressListeners += listener
        return AutoCloseable { progressListeners.remove(listener) }
    }

    /**
     * Publish one todo change event to all listeners.
     *
     * @param change Event payload to broadcast
     */
    fun publish(change: TodoChange) {
        listeners.forEach { listener ->
            runCatching { listener(change) }
        }
    }

    /**
     * Publish one transient LLM response update without changing persisted todo state.
     *
     * @param update Response delta and stream state
     */
    fun publishProgress(update: TodoProgressUpdate) {
        progressListeners.forEach { listener ->
            runCatching { listener(update) }
        }
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
)
