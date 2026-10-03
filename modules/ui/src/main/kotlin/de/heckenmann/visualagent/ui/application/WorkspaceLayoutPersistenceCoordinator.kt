package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.protocol.WorkspaceLayoutPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns pending presentation layout updates and their ordered shutdown flush. */
class WorkspaceLayoutPersistenceCoordinator(
    private val port: WorkspaceLayoutPort,
) {
    private val pending = MutableStateFlow<WorkspaceLayoutUpdate?>(null)
    private val writes = Mutex()
    private var published: WorkspaceLayoutUpdate? = null
    private var persistedWindows: List<LayoutWindowState>? = null
    private var finished = false

    internal val updates: StateFlow<WorkspaceLayoutUpdate?> = pending

    /** Publishes presentation state without performing protocol or database work. */
    fun update(
        stage: LayoutSize,
        desktop: LayoutSize,
        windows: List<LayoutWindowState>,
    ) {
        pending.value = WorkspaceLayoutUpdate(stage, desktop, windows.toList())
    }

    internal suspend fun publishLatest() =
        withContext(Dispatchers.IO) {
            writes.withLock {
                if (!finished) publishPending()
            }
        }

    internal suspend fun flush() =
        withContext(Dispatchers.IO + NonCancellable) {
            writes.withLock {
                if (!finished) publishPending()
            }
        }

    /** Flushes the latest accepted state before closing protocol/server resources and stops future writes. */
    suspend fun finish() =
        withContext(Dispatchers.IO + NonCancellable) {
            writes.withLock {
                if (!finished) {
                    try {
                        publishPending()
                    } finally {
                        finished = true
                    }
                }
            }
        }

    private fun publishPending() {
        val update = pending.value ?: return
        if (update == published) return
        port.bind(update.stage, update.desktop, update.windows)
        if (update.windows != persistedWindows) {
            port.applyWindowStates(update.windows, notifyListeners = false)
            persistedWindows = update.windows
        }
        published = update
    }
}

/** Immutable pending presentation geometry and panel configuration. */
internal data class WorkspaceLayoutUpdate(
    val stage: LayoutSize,
    val desktop: LayoutSize,
    val windows: List<LayoutWindowState>,
)
