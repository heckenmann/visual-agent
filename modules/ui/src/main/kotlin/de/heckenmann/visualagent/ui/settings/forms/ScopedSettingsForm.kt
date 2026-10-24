package de.heckenmann.visualagent.ui.settings.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSection
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.PanelScrollbarHost
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar
import de.heckenmann.visualagent.ui.components.settingsDraftActionRow
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.settings.navigation.SettingsPageDraft
import de.heckenmann.visualagent.ui.workspace.PanelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads a category draft off-thread and persists only that category's owned fields. */
@Composable
internal fun ScopedSettingsForm(
    port: SettingsPort,
    section: SettingsSection,
    state: SettingsPageDraft = remember(port, section) { SettingsPageDraft() },
    onSaved: () -> Unit = {},
    content: @Composable (SettingsSnapshot, (SettingsSnapshot) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var persisted by state.persisted
    var draft by state.draft
    var loaded by state.loaded
    var saving by state.saving
    var status by state.status
    val dirty = loaded && draft != persisted

    /** Reloads this category after explicit reset or initial entry. */
    fun reload() {
        scope.launch {
            try {
                val snapshot = port.snapshotAsync()
                persisted = snapshot
                draft = snapshot
                loaded = true
                status = "Settings loaded"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = error.toUiErrorMessage()
            }
        }
    }
    LaunchedEffect(port, section) { if (!loaded || draft == persisted) reload() }
    DisposableEffect(port) {
        val listener =
            port.addChangeListener { next ->
                scope.launch {
                    if (!saving && draft == persisted) {
                        persisted = next
                        draft = next
                        loaded = true
                    }
                }
            }
        onDispose { listener.close() }
    }
    Column(Modifier.fillMaxSize()) {
        val scroll = rememberScrollState()
        PanelScrollbarHost(Modifier.weight(1f).fillMaxWidth()) {
            RegisterPanelVerticalScrollbar(scroll)
            Column(Modifier.verticalScroll(scroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loaded) content(draft) { draft = it }
            }
        }
        HorizontalDivider()
        settingsDraftActionRow(
            hasUnsavedChanges = dirty,
            saving = saving,
            onReset = ::reload,
            onSave = {
                if (!saving && dirty) {
                    val submitted = draft
                    saving = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { port.saveSection(section, submitted) }
                            persisted = submitted
                            status = "Saved ${section.name.lowercase()} settings"
                            onSaved()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            status = error.toUiErrorMessage()
                        } finally {
                            saving = false
                        }
                    }
                }
            },
        )
        PanelStatus(status)
    }
}
