package de.heckenmann.visualagent.config

/**
 * Immutable notification payload emitted when one AppConfig value changes.
 *
 * @property key Stable persisted preference key
 * @property oldValue Previous string value, or null when no value was known
 * @property newValue Current string value
 */
data class AppConfigChange(
    val key: String,
    val oldValue: String?,
    val newValue: String,
)

internal data class AppConfigSnapshot(
    val values: Map<String, String>,
)

/** Controls where the conversation composer is rendered in the conversation panel. */
enum class ConversationInputPlacement {
    /** Keep the composer anchored to the bottom of the conversation panel. */
    FIXED,

    /** Render the composer as a message-like card at the latest conversation end. */
    CONVERSATION_MESSAGE,
    ;

    companion object {
        /** Parse a persisted placement, falling back to the conversation message behavior. */
        fun fromString(value: String?): ConversationInputPlacement =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CONVERSATION_MESSAGE
    }
}
