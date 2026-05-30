package de.heckenmann.visualagent.agent

/**
 * Event emitted by AgentManager for session-level configuration changes.
 *
 * This allows UI components to synchronize state without polling.
 */
sealed interface SessionEvent {
    /**
     * A session setting was changed.
     *
     * @param key Setting key (e.g., "model", "streaming", "contextLength")
     * @param value New setting value
     */
    data class SettingChanged(
        val key: String,
        val value: Any,
    ) : SessionEvent
}
