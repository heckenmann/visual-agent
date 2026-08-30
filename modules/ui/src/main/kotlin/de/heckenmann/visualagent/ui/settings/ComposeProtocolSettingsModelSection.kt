package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelCheckbox
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSelectOption

/** Renders staged model selection and model-specific actions inside the provider connection section. */
@Composable
internal fun modelSettingsContent(
    providerId: String,
    modelId: String,
    models: List<ProviderModel>,
    loadingModels: Boolean,
    modelDetails: String,
    favoriteModels: List<String>,
    onModelSelected: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
) {
    PanelDropdownField(
        label = "Model",
        selectedValue = modelId,
        options = models.map { PanelSelectOption(it.id, it.name) },
        onSelected = onModelSelected,
        enabled = models.isNotEmpty(),
        information = "Selects the model used for future main-agent requests through the selected provider.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionIconButton(
            icon = Icons.Filled.Refresh,
            description = "Refresh models",
            enabled = !loadingModels,
            onClick = onRefreshModels,
        )
        PanelCheckbox(
            label = "Favorite",
            checked = modelId in favoriteModels,
            enabled = modelId.isNotBlank(),
            onCheckedChange = onFavoriteChanged,
            information = "Keeps this model in the saved favorites list for quick selection.",
        )
    }
    PanelInfoBox(modelDetails)
}
