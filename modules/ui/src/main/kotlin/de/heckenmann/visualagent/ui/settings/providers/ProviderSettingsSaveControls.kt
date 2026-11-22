package de.heckenmann.visualagent.ui.settings.providers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.ui.components.settingsDraftActionRow
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.workspace.PanelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saves the staged provider catalog without changing its active selection. */
@Composable
internal fun ProviderSettingsSaveControls(
    port: ProviderPort,
    state: ProviderManagementState,
    dirty: Boolean,
    onReset: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    settingsDraftActionRow(
        dirty,
        state.busy,
        canSave = state.modelOptions.values.all(::validModelOptions),
        onReset = onReset,
        onSave = {
            val submitted = state.draft.providers
            val baseline = state.persisted.providers
            state.busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        port.replaceProfiles(submitted, baseline)
                    }
                    state.persisted = state.persisted.copy(providers = submitted)
                    state.optionsDirty = false
                    state.status = "Saved provider settings"
                    onSaved()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    state.status = error.toUiErrorMessage()
                } finally {
                    state.busy = false
                }
            }
        },
    )
    PanelStatus(state.status)
}
