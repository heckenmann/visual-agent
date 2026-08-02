package de.heckenmann.visualagent.agent

import org.springframework.stereotype.Component

/**
 * Spring-managed adapter that replaces the static [AgentManager.Companion.globalAgentCallback].
 *
 * The UI registers a callback via [register], and [AgentManager] calls [notify] directly
 * instead of going through a static companion field.
 */
@Component
class AgentStatusCallbackAdapter {
    private var callback: ((String, String) -> Unit)? = null

    /**
     * Registers the UI callback that receives sub-agent lifecycle notifications.
     *
     * @param callback Callback invoked with agent ID and user-facing message
     */
    fun register(callback: (String, String) -> Unit) {
        this.callback = callback
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
    }
}
