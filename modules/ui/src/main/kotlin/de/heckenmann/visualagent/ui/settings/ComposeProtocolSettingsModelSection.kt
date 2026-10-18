package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CapabilitySupport
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.capabilitySupport
import de.heckenmann.visualagent.ui.components.PanelCheckbox
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.components.labelWithCapabilities

/** Renders staged model selection and model-specific actions inside the provider connection section. */
@Composable
internal fun modelSettingsContent(
    providerId: String,
    modelId: String,
    models: List<ProviderModel>,
    contextLength: Int,
    contextLimit: Int?,
    loadingModels: Boolean,
    modelDetails: String,
    favoriteModels: List<String>,
    onModelSelected: (String) -> Unit,
    onContextLengthChanged: (Int) -> Unit,
    onRefreshModels: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onModelCapabilitiesChanged: (String, Boolean?) -> Unit = { _, _ -> },
) {
    PanelDropdownField(
        label = "Model",
        selectedValue = modelId,
        options =
            models.map { model -> PanelSelectOption(model.id, model.labelWithCapabilities()) },
        onSelected = onModelSelected,
        enabled = models.isNotEmpty(),
        information = "Selects the model used for future main-agent requests through the selected provider.",
    )
    if (models.firstOrNull { it.id == modelId }?.capabilitySupport("chat") == CapabilitySupport.UNSUPPORTED) {
        PanelInfoBox(
            "This model's current capability declaration excludes chat. Review its capability settings below; the declaration does not remove the model from this settings catalog.",
        )
    }
    modelContextLengthSetting(contextLength, contextLimit, onContextLengthChanged)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            enabled = !loadingModels,
            onClick = onRefreshModels,
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Refresh models")
        }
        PanelCheckbox(
            label = "Favorite",
            checked = modelId in favoriteModels,
            enabled = modelId.isNotBlank(),
            onCheckedChange = onFavoriteChanged,
            information = "Keeps this model in the saved favorites list for quick selection.",
        )
    }
    PanelInfoBox(modelDetails)
    ModelCapabilitiesEditor(models.firstOrNull { it.id == modelId }, onModelCapabilitiesChanged)
}
