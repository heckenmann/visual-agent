package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar
import de.heckenmann.visualagent.ui.components.settingsDraftActionRow
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.workspace.PanelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Renders draft-based execution and appearance settings through the neutral protocol port. */
@Composable
internal fun settingsPanel(
    settingsPort: SettingsPort,
    onSettingsChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var persisted by remember { mutableStateOf(SettingsSnapshot()) }
    var draft by remember { mutableStateOf(SettingsSnapshot()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading settings...") }
    val hasUnsavedChanges = loaded && draft != persisted
    val hasUnsavedChangesState by rememberUpdatedState(hasUnsavedChanges)

    /** Loads persisted settings, optionally reporting that local edits were discarded. */
    fun loadPersistedDraft(discardingLocalEdits: Boolean) {
        if (saving) return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { settingsPort.snapshotAsync() } }
                .onSuccess { current ->
                    persisted = current
                    draft = current
                    loaded = true
                    status = if (discardingLocalEdits) "Discarded unsaved changes" else "Settings loaded"
                }.onFailure { error -> status = error.toUiErrorMessage() }
        }
    }

    /** Applies the complete local draft only after the user explicitly chooses Save. */
    fun saveDraft() {
        if (!loaded || saving || !hasUnsavedChanges) return
        saving = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { settingsPort.save(draft) } }
                .onSuccess {
                    persisted = draft
                    status = "Saved settings"
                    onSettingsChanged()
                }.onFailure { error -> status = error.toUiErrorMessage() }
            saving = false
        }
    }

    LaunchedEffect(settingsPort) { loadPersistedDraft(discardingLocalEdits = false) }
    DisposableEffect(settingsPort) {
        val handle =
            settingsPort.addChangeListener { current ->
                scope.launch {
                    if (!hasUnsavedChangesState) {
                        persisted = current
                        draft = current
                        loaded = true
                    }
                }
            }
        onDispose { handle.close() }
    }

    val scrollState = rememberScrollState()
    RegisterPanelVerticalScrollbar(scrollState)
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(scrollState),
            ) {
                AppearanceSettingsSection(draft) { draft = it }
            }
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            settingsDraftActionRow(
                hasUnsavedChanges = hasUnsavedChanges,
                saving = saving,
                onReset = { loadPersistedDraft(discardingLocalEdits = true) },
                onSave = ::saveDraft,
            )
            PanelStatus(status)
        }
    }
}
