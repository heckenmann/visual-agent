package de.heckenmann.visualagent.agent.tools

import java.util.concurrent.atomic.AtomicInteger

/** Keeps batch admission held until both the subscription and synchronous work have ended. */
class ToolWorkLease(
    private val release: () -> Unit,
) : AutoCloseable {
    private val references = AtomicInteger(1)

    /** Retains admission for actual work, or rejects work whose subscription has already ended. */
    fun retain(): Boolean {
        while (true) {
            val current = references.get()
            if (current == 0) return false
            if (references.compareAndSet(current, current + 1)) return true
        }
    }

    /** Releases one owner; the final owner returns the admission permits. */
    override fun close() {
        if (references.decrementAndGet() == 0) release()
    }
}

/** Retains admission around synchronous work embedded in a reactive tool implementation. */
internal fun <T> trackedToolWork(
    context: Map<String, Any>,
    work: () -> T,
): T {
    val lease = context["toolWorkLease"] as? ToolWorkLease
    if (lease != null && !lease.retain()) throw java.util.concurrent.CancellationException("Tool work cancelled before start")
    try {
        return work()
    } finally {
        lease?.close()
    }
}
