@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfig

@Composable
internal fun SettingsProviderSection(
    providers: List<ProviderProfile>,
    providerId: String,
    activeProvider: ProviderProfile?,
    modelId: String,
    modelSelection: String,
    customModelId: String,
    baseUrl: String,
    apiKey: String,
    apiKeyVisible: Boolean,
    modelSearch: String,
    favoritesOnly: Boolean,
    favoriteModels: Set<String>,
    resolvedModel: String,
    modelDetails: String,
    loadingModels: Boolean,
    loadingDetails: Boolean,
    filteredModels: List<ProviderModelConfig>,
    config: AppConfig,
    providerCatalogService: ProviderCatalogService,
    modalRequester: ComposeModalRequester,
    onProviderSelected: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiKeyVisibleToggle: () -> Unit,
    onModelSearchChange: (String) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onFavoriteModelsChange: (Set<String>) -> Unit,
    onModelSelected: (String) -> Unit,
    onCustomModelChange: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onRefreshDetails: () -> Unit,
    onProviderAdded: (ProviderProfile) -> Unit,
    onProviderEdited: (ProviderProfile) -> Unit,
    onProviderDeleted: (ProviderProfile) -> Unit,
) {
    PanelSection(title = "Provider and model") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelDropdownField(
                label = "Provider",
                selectedValue = providerId,
                options = providers.map { option -> PanelSelectOption(option.id, option.providerDisplayName()) },
                onSelected = onProviderSelected,
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.Add,
                description = "Add provider",
                onClick = {
                    modalRequester.request(
                        ComposeContentModal(title = "Add provider") { dismiss ->
                            ProviderProfileEditor(
                                initial = newProviderFormState(),
                                existing = null,
                                canDisable = true,
                                onCancel = dismiss,
                                onSave = { profile ->
                                    onProviderAdded(profile)
                                    dismiss()
                                },
                            )
                        },
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Edit,
                description = "Edit provider",
                enabled = activeProvider != null,
                onClick = {
                    val current = activeProvider ?: return@ActionIconButton
                    modalRequester.request(
                        ComposeContentModal(title = "Edit provider") { dismiss ->
                            ProviderProfileEditor(
                                initial = current.toFormState(),
                                existing = current,
                                canDisable = providers.size > 1,
                                onCancel = dismiss,
                                onSave = { profile ->
                                    onProviderEdited(profile)
                                    dismiss()
                                },
                            )
                        },
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Delete provider",
                enabled = providers.size > 1 && activeProvider != null,
                onClick = {
                    val current = activeProvider ?: return@ActionIconButton
                    modalRequester.requestConfirmation(
                        ComposeConfirmationModal(
                            title = "Delete provider?",
                            message = "Delete '${current.name}' from the provider catalog.",
                            confirmDescription = "Delete provider",
                        ) {
                            onProviderDeleted(current)
                        },
                    )
                },
            )
        }
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                ActionIconButton(
                    icon = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    description = if (apiKeyVisible) "Hide API key" else "Show API key",
                    onClick = onApiKeyVisibleToggle,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = modelSearch,
                onValueChange = onModelSearchChange,
                label = { Text("Search models") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PanelCheckbox(label = "Favorites", checked = favoritesOnly, onCheckedChange = onFavoritesOnlyChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelDropdownField(
                label = "Model",
                selectedValue = modelSelection,
                options =
                    filteredModels.map { model -> PanelSelectOption(model.id, model.modelDisplayName()) } +
                        PanelSelectOption(CUSTOM_MODEL_ID, "Custom model ID"),
                onSelected = onModelSelected,
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = if (resolvedModel in favoriteModels) Icons.Filled.Star else Icons.Filled.StarBorder,
                description = if (resolvedModel in favoriteModels) "Remove model favorite" else "Add model favorite",
                enabled = resolvedModel.isNotBlank(),
                onClick = {
                    onFavoriteModelsChange(
                        if (resolvedModel in favoriteModels) favoriteModels - resolvedModel else favoriteModels + resolvedModel,
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Refresh models",
                enabled = !loadingModels && providerId.isNotBlank(),
                onClick = onRefreshModels,
            )
        }
        if (modelSelection == CUSTOM_MODEL_ID) {
            OutlinedTextField(
                value = customModelId,
                onValueChange = onCustomModelChange,
                label = { Text("Custom model ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ActionIconButton(
            icon = Icons.Filled.Refresh,
            description = "Refresh model details",
            enabled = !loadingDetails && resolvedModel.isNotBlank(),
            onClick = onRefreshDetails,
        )
        PanelInfoBox(modelDetails)
    }
}

internal fun ProviderProfile.providerDisplayName(): String = "$name ($id)"

internal fun ProviderModelConfig.modelDisplayName(): String =
    if (name != id) {
        "$name ($id)"
    } else {
        id
    }
