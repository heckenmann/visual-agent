package de.heckenmann.visualagent.ui.settings.navigation

import androidx.compose.runtime.mutableStateOf
import de.heckenmann.visualagent.protocol.SettingsSnapshot

/** Keeps unsaved fields alive when users switch Settings destinations. */
internal class SettingsPageDraft {
    val persisted = mutableStateOf(SettingsSnapshot())
    val draft = mutableStateOf(SettingsSnapshot())
    val loaded = mutableStateOf(false)
    val saving = mutableStateOf(false)
    val status = mutableStateOf("Loading settings...")
}
