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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSection
import de.heckenmann.visualagent.protocol.UpdatePort
import de.heckenmann.visualagent.protocol.UpdateRequest
import de.heckenmann.visualagent.protocol.UpdateStatus
import de.heckenmann.visualagent.ui.application.UpdatePresentationState
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar
import de.heckenmann.visualagent.ui.components.settingsDraftActionRow
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.settings.navigation.SettingsPageDraft
import de.heckenmann.visualagent.ui.workspace.PanelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Renders draft-based execution and appearance settings through the neutral protocol port. */
@Composable
internal fun settingsPanel(
    settingsPort: SettingsPort,
    onSettingsChanged: () -> Unit,
    updatePort: UpdatePort = DisabledUpdatePort,
    updateState: UpdatePresentationState = UpdatePresentationState(),
    onUpdateStateChanged: (UpdatePresentationState) -> Unit = {},
    section: SettingsSection = SettingsSection.APPEARANCE,
    state: SettingsPageDraft = remember(settingsPort, section) { SettingsPageDraft() },
) {
    val scope = rememberCoroutineScope()
    var persisted by state.persisted
    var draft by state.draft
    var loaded by state.loaded
    var saving by state.saving
    var status by state.status
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

    /** Applies only staged appearance fields while preserving newer settings from the server. */
    fun saveDraft() {
        if (!loaded || saving || !hasUnsavedChanges) return
        saving = true
        val submitted = draft
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    settingsPort.saveSection(section, submitted)
                    submitted
                }
            }.onSuccess { saved ->
                persisted = saved
                if (draft == submitted) draft = saved
                status = "Saved settings"
                onSettingsChanged()
            }.onFailure { error -> status = error.toUiErrorMessage() }
            saving = false
        }
    }

    LaunchedEffect(settingsPort, section) { if (!loaded || draft == persisted) loadPersistedDraft(discardingLocalEdits = false) }
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
                if (section == SettingsSection.APPEARANCE) {
                    AppearanceSettingsSection(draft) { draft = it }
                } else {
                    UpdateSettingsSection(
                        settings = draft,
                        updatePort = updatePort,
                        initialState = updateState,
                        onStateChanged = onUpdateStateChanged,
                        onChange = { draft = it },
                    )
                }
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

private object DisabledUpdatePort : UpdatePort {
    override fun check(request: UpdateRequest): UpdateStatus =
        UpdateStatus(
            currentVersion = "unknown",
            latestVersion = null,
            updateAvailable = false,
            channel = "stable",
            releaseName = null,
            releaseNotes = null,
            releaseUrl = null,
            selectedAsset = null,
            availableAssets = emptyList(),
            error = "Update service is unavailable",
        )

    override fun download(request: UpdateRequest) =
        de.heckenmann.visualagent.protocol
            .UpdateDownloadResult(error = "Update service is unavailable")

    override fun install(stagedId: String) =
        de.heckenmann.visualagent.protocol
            .UpdateInstallResult(false, "Update service is unavailable")
}
