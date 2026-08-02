package de.heckenmann.visualagent.agent

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight cancellation token shared between UI and long-running work.
 *
 * A token starts active. Calling [cancel] moves it to a cancelled state and invokes
 * every registered listener. Long-running code can check [isCancelled] or call
 * [throwIfCancelled] to abort cooperatively.
 *
 * Tokens are single-use: after [cancel] has been called the token stays cancelled.
 *
 * @see docs/usecases/uc_0000078_cancel_main_agent_response.md
 * @see docs/usecases/uc_0000079_cancel_sub_agent_job.md
 * @see docs/usecases/uc_0000080_cancel_all_running_actions.md
 */
class CancellationToken {
    private val cancelled = AtomicBoolean(false)
    private val listeners = mutableListOf<() -> Unit>()
    private val lock = Any()

    /** `true` after [cancel] has been called. */
    val isCancelled: Boolean
        get() = cancelled.get()

    /** Cancels this token and notifies all registered listeners. Idempotent. */
    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        synchronized(lock) {
            listeners.forEach { listener ->
                runCatching { listener() }.onFailure { }
            }
        }
    }

    /**
     * Registers a listener that is invoked when this token is cancelled.
     *
     * If the token is already cancelled the listener is invoked immediately.
     *
     * @param listener Callback executed exactly once on cancellation
     */
    fun onCancelled(listener: () -> Unit) {
        val shouldInvoke =
            synchronized(lock) {
                if (isCancelled) {
                    true
                } else {
                    listeners += listener
                    false
                }
            }
        if (shouldInvoke) {
            runCatching { listener() }.onFailure { }
        }
    }

    /**
     * Throws [CancellationException] when this token has been cancelled.
     *
     * Long-running suspending code can call this between chunks to react quickly to
     * user cancellation.
     */
    fun throwIfCancelled() {
        if (isCancelled) throw CancellationException("Request cancelled by user")
    }
}
