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
     * Publish one todo change event to all listeners.
     *
     * @param change Event payload to broadcast
     */
    fun publish(change: TodoChange) {
        listeners.forEach { listener ->
            runCatching { listener(change) }
        }
    }
}
