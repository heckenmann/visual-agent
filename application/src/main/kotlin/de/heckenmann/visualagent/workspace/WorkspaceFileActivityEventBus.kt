package de.heckenmann.visualagent.workspace

import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/** One completed mutation of the managed workspace filesystem. */
data class WorkspaceFileActivity(
    val message: String,
)

/** Publishes managed workspace mutations for conversation persistence. */
@Component
class WorkspaceFileActivityEventBus {
    private val listeners = CopyOnWriteArrayList<(WorkspaceFileActivity) -> Unit>()

    /** Registers a listener for future managed workspace mutations. */
    fun addListener(listener: (WorkspaceFileActivity) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    /** Publishes one completed managed workspace mutation. */
    fun publish(activity: WorkspaceFileActivity) {
        listeners.forEach { listener -> runCatching { listener(activity) } }
    }
}
