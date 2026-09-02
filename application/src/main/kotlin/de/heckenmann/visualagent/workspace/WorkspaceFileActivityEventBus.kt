package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

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
