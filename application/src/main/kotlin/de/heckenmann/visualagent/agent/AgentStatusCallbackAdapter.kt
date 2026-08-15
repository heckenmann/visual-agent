package de.heckenmann.visualagent.agent

import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Spring-managed adapter that replaces the static [AgentManager.Companion.globalAgentCallback].
 *
 * The UI registers a callback via [register], and [AgentManager] calls [notify] directly
 * instead of going through a static companion field.
 */
@Component
class AgentStatusCallbackAdapter {
    private var callback: ((String, String) -> Unit)? = null
    private val listeners = CopyOnWriteArrayList<(String, String) -> Unit>()

    /**
     * Registers the UI callback that receives sub-agent lifecycle notifications.
     *
     * @param callback Callback invoked with agent ID and user-facing message
     */
    fun register(callback: (String, String) -> Unit) {
        this.callback = callback
    }

    /** Registers an additive listener for the transport boundary. */
    fun addListener(listener: (String, String) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * Notifies the registered callback (if any) of a sub-agent lifecycle event.
     *
     * @param agentId Sub-agent identifier
     * @param message User-facing message
     */
    fun notify(
        agentId: String,
        message: String,
    ) {
        callback?.invoke(agentId, message)
        listeners.forEach { listener -> runCatching { listener(agentId, message) } }
    }
}
