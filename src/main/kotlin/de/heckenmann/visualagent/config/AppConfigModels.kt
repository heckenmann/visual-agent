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
