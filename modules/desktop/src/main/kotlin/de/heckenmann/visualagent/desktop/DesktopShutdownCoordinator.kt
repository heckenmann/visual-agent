package de.heckenmann.visualagent.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** Coordinates one application-exit request and one final server-resource cleanup. */
internal class DesktopShutdownCoordinator {
    private val exitRequested = AtomicBoolean(false)
    private val cleanupLock = Any()
    private var cleanup: CompletableFuture<Void>? = null

    /**
     * Claim the application exit transition.
     *
     * @return `true` only for the first caller
     */
    fun requestExit(): Boolean = exitRequested.compareAndSet(false, true)

    /**
     * Schedule startup resource cleanup once without blocking composition disposal.
     *
     * @param closeResources Resource cleanup operation
     */
    fun closeResources(closeResources: () -> Unit) {
        synchronized(cleanupLock) {
            if (cleanup == null) {
                cleanup = CompletableFuture.runAsync(closeResources, Dispatchers.IO.asExecutor())
            }
        }
    }

    /** Waits on the process entry thread after Compose has stopped, never on the UI thread. */
    fun awaitResourceCleanup() {
        val completion = synchronized(cleanupLock) { cleanup }
        completion?.join()
    }
}
