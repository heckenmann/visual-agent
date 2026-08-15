package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.NumericPanelField
import de.heckenmann.visualagent.ui.components.PanelCheckbox
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import de.heckenmann.visualagent.ui.workspace.PanelStatus

/** Renders settings through the transport-neutral application boundary. */
@Suppress("FunctionName")
@Composable
internal fun SettingsPanel(
    settingsPort: SettingsPort,
    providerPort: ProviderPort,
    modalRequester: ComposeModalRequester,
    onSettingsChanged: () -> Unit,
    inFlight: InFlightStateHolder,
    activityPort: ActivityPort,
) {
    var snapshot by remember { mutableStateOf(settingsPort.snapshot()) }
    var providers by remember { mutableStateOf(providerPort.enabledProviders()) }
    var providerId by remember { mutableStateOf(providerPort.activeProviderId()) }
    var modelId by remember { mutableStateOf(providerPort.activeModelId()) }
    var status by remember { mutableStateOf("Ready") }
    DisposableEffect(settingsPort, providerPort) {
        val settingsHandle = settingsPort.addChangeListener { snapshot = it }
        val providerHandle =
            providerPort.addChangeListener {
                providers = providerPort.enabledProviders()
                providerId = providerPort.activeProviderId()
                modelId = providerPort.activeModelId()
            }
        onDispose {
            settingsHandle.close()
            providerHandle.close()
        }
    }
    val save = {
        settingsPort.save(snapshot.copy(providerId = providerId, modelId = modelId))
        providerPort.setActiveSelection(providerId, modelId)
        onSettingsChanged()
        status = "Saved settings"
    }
    val models = providerPort.selectableModels(providerId)
    val scrollState = rememberScrollState()
    RegisterPanelVerticalScrollbar(scrollState)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
    ) {
        PanelSection(title = "Provider and model") {
            PanelDropdownField(
                label = "Provider",
                selectedValue = providerId,
                options = providers.map { PanelSelectOption(it.id, it.name) },
                onSelected = {
                    providerId = it
                    modelId =
                        providerPort
                            .selectableModels(it)
                            .firstOrNull()
                            ?.id
                            .orEmpty()
                },
            )
            PanelDropdownField(
                label = "Model",
                selectedValue = modelId,
                options = models.map { PanelSelectOption(it.id, it.name) },
                onSelected = { modelId = it },
            )
            ActionIconButton(icon = Icons.Filled.Save, description = "Save provider and model", onClick = save)
        }
        PanelSection(title = "Execution and appearance") {
            NumericPanelField(
                label = "Context length",
                value = snapshot.contextLength.toString(),
                onValueChange = { value ->
                    snapshot = snapshot.copy(contextLength = value.toIntOrNull()?.coerceIn(1024, 32768) ?: snapshot.contextLength)
                },
            )
            NumericPanelField(
                label = "Startup history",
                value = snapshot.loadLimit.toString(),
                onValueChange = { value ->
                    snapshot = snapshot.copy(loadLimit = value.toIntOrNull()?.coerceIn(1, 1000) ?: snapshot.loadLimit)
                },
            )
            NumericPanelField(
                label = "Parallel agents",
                value = snapshot.maxParallelSubAgents.toString(),
                onValueChange = { value ->
                    snapshot =
                        snapshot.copy(
                            maxParallelSubAgents = value.toIntOrNull()?.coerceIn(1, 20) ?: snapshot.maxParallelSubAgents,
                        )
                },
            )
            PanelCheckbox(
                label = "Stream",
                checked = snapshot.streamingEnabled,
                onCheckedChange = { snapshot = snapshot.copy(streamingEnabled = it) },
            )
            PanelCheckbox(
                label = "Compaction",
                checked = snapshot.autoCompactionEnabled,
                onCheckedChange = { snapshot = snapshot.copy(autoCompactionEnabled = it) },
            )
            OutlinedTextField(
                value = snapshot.userModelInstruction,
                onValueChange = { snapshot = snapshot.copy(userModelInstruction = it) },
                label = { Text("Model instruction") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionIconButton(icon = Icons.Filled.Save, description = "Save settings", onClick = save)
        }
        PanelStatus(status)
    }
}
