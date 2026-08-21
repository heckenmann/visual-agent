package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.protocol.DownloadActivity
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/** Publishes lifecycle transitions for server-owned workspace downloads. */
@Component
class WorkspaceDownloadEventBus {
    private val listeners = CopyOnWriteArrayList<(DownloadActivity) -> Unit>()

    /** Registers a listener for future download status transitions. */
    fun addListener(listener: (DownloadActivity) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    /** Publishes one download status transition to all listeners. */
    fun publish(event: DownloadActivity) {
        listeners.forEach { listener -> runCatching { listener(event) } }
    }
}
