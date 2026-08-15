package de.heckenmann.visualagent.protocol

/** Lifecycle state exposed to presentation code without coupling it to Spring. */
interface LifecyclePort {
    /** True after shutdown has started and new work must not be accepted. */
    val closing: Boolean

    /** Marks the application as shutting down. */
    fun beginShutdown()
}

/** In-memory lifecycle implementation for local presentation state and tests. */
class LifecycleState : LifecyclePort {
    @Volatile
    override var closing: Boolean = false

    override fun beginShutdown() {
        closing = true
    }
}
