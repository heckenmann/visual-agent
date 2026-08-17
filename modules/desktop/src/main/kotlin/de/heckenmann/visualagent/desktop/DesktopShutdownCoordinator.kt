package de.heckenmann.visualagent.desktop

import java.util.concurrent.atomic.AtomicBoolean

/** Coordinates one application-exit request and one final server-resource cleanup. */
internal class DesktopShutdownCoordinator {
    private val exitRequested = AtomicBoolean(false)
    private val resourcesClosed = AtomicBoolean(false)

    /**
     * Claim the application exit transition.
     *
     * @return `true` only for the first caller
     */
    fun requestExit(): Boolean = exitRequested.compareAndSet(false, true)

    /**
     * Close startup resources once, even when a window callback and composition disposal race.
     *
     * @param closeResources Resource cleanup operation
     */
    fun closeResources(closeResources: () -> Unit) {
        if (resourcesClosed.compareAndSet(false, true)) {
            closeResources()
        }
    }
}
