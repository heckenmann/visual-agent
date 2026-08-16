package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelCheckbox
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.PanelSelectOption

/** Renders model selection and model-specific actions for the settings panel. */
@Suppress("FunctionName")
@Composable
internal fun ModelSettingsSection(
    providerId: String,
    modelId: String,
    models: List<ProviderModel>,
    loadingModels: Boolean,
    loadingDetails: Boolean,
    modelDetails: String,
    favoriteModels: List<String>,
    snapshotLoaded: Boolean,
    canSaveSelection: Boolean,
    onModelSelected: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onRefreshDetails: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onSaveSelection: () -> Unit,
) {
    PanelSection(title = "Main agent model") {
        PanelDropdownField(
            label = "Model",
            selectedValue = modelId,
            options = models.map { PanelSelectOption(it.id, it.name) },
            onSelected = onModelSelected,
            enabled = models.isNotEmpty(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Refresh models",
                enabled = !loadingModels,
                onClick = onRefreshModels,
            )
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Refresh model details",
                enabled = !loadingDetails && modelId.isNotBlank(),
                onClick = onRefreshDetails,
            )
            PanelCheckbox(
                label = "Favorite",
                checked = modelId in favoriteModels,
                enabled = modelId.isNotBlank(),
                onCheckedChange = onFavoriteChanged,
            )
        }
        PanelInfoBox(modelDetails)
        ActionIconButton(
            icon = Icons.Filled.Save,
            description = "Save provider and model",
            enabled = snapshotLoaded && canSaveSelection && providerId.isNotBlank(),
            onClick = onSaveSelection,
        )
    }
}
