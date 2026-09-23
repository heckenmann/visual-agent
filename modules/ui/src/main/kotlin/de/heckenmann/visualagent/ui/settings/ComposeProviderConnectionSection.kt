package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.PanelSelectOption

/** Renders provider, model, and onboarding controls for the main-agent connection. */
@Composable
internal fun providerConnectionSection(
    draft: ProviderSettingsDraft,
    enabledProviders: List<ProviderProfile>,
    selectedProvider: ProviderProfile?,
    models: List<ProviderModel>,
    refreshing: Boolean,
    onRunOnboarding: () -> Unit,
    onProviderSelected: (String) -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: () -> Unit,
    onRemoveProvider: () -> Unit,
    onModelSelected: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
) {
    PanelSection(title = "Main agent connection") {
        OutlinedButton(
            onClick = onRunOnboarding,
            modifier = Modifier.semantics { contentDescription = "Run onboarding again" },
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Run onboarding again")
        }
        PanelInfoBox(
            "Reopens the guided provider and model readiness check without changing saved settings until you finish it.",
        )
        PanelDropdownField(
            label = "Provider",
            selectedValue = draft.providerId,
            options = enabledProviders.map { profile -> PanelSelectOption(profile.id, profile.name) },
            enabled = enabledProviders.isNotEmpty(),
            onSelected = onProviderSelected,
            information =
                "Selects the connection and credentials used by the main agent. " +
                    "It also changes the available model catalog.",
        )
        providerProfileActions(
            canEdit = selectedProvider != null,
            canRemove = draft.providers.size > 1 && selectedProvider != null,
            onAdd = onAddProvider,
            onEdit = onEditProvider,
            onRemove = onRemoveProvider,
        )
        HorizontalDivider()
        modelSettingsContent(
            providerId = draft.providerId,
            modelId = draft.modelId,
            models = models,
            loadingModels = refreshing,
            modelDetails = "Select a model and save to use it for new agent requests.",
            favoriteModels = draft.favoriteModels.toList(),
            onModelSelected = onModelSelected,
            onRefreshModels = onRefreshModels,
            onFavoriteChanged = onFavoriteChanged,
        )
    }
}
