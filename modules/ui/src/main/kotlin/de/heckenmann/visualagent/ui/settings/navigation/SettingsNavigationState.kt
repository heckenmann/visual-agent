package de.heckenmann.visualagent.ui.settings.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Stable destinations shared by Settings navigation and contextual entry points. */
enum class SettingsDestination(
    val label: String,
) {
    APPEARANCE("Appearance"),
    PROVIDERS("Providers"),
    AGENT("Agent"),
    UPDATES("Updates"),
}

/** Composition-owned navigation that can reveal a specific Settings destination. */
class SettingsNavigationState {
    /** Currently selected destination; presentation only, never a server preference. */
    var destination: SettingsDestination by mutableStateOf(SettingsDestination.APPEARANCE)
}
