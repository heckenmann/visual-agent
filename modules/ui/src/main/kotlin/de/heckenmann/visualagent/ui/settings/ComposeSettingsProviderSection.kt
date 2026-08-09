@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.codex.CodexCliAccountService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*

@Composable
internal fun SettingsProviderSection(
    providers: List<ProviderProfile>,
    providerProfiles: List<ProviderProfile>,
    providerId: String,
    managedProvider: ProviderProfile?,
    modelSearch: String,
    favoritesOnly: Boolean,
    favoriteModels: Set<String>,
    resolvedModel: String,
    modelDetails: String,
    loadingModels: Boolean,
    loadingDetails: Boolean,
    filteredModels: List<ProviderModelConfig>,
    modalRequester: ComposeModalRequester,
    codexCliAccountService: CodexCliAccountService?,
    onProviderSelected: (String) -> Unit,
    onModelSearchChange: (String) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onFavoriteModelsChange: (Set<String>) -> Unit,
    onModelSelected: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onRefreshDetails: () -> Unit,
    onProviderAdded: (ProviderProfile) -> Unit,
    onProviderEdited: (ProviderProfile) -> Unit,
    onProviderDeleted: (ProviderProfile) -> Unit,
) {
    PanelSection(title = "Provider connections") {
        Text("Choose the connection the main agent should use.")
        ProviderSelectionList(
            providers = providers,
            selectedProviderId = providerId,
            activeProviderId = providerId,
            onSelected = onProviderSelected,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(
                icon = Icons.Filled.Add,
                description = "Add provider",
                onClick = {
                    modalRequester.requestProviderProfileDialog(
                        profile = null,
                        canDisable = true,
                        codexCliAccountService = codexCliAccountService,
                        onSave = onProviderAdded,
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Edit,
                description = "Edit provider",
                enabled = managedProvider != null,
                onClick = {
                    val current = managedProvider ?: return@ActionIconButton
                    modalRequester.requestProviderProfileDialog(
                        profile = current,
                        canDisable = providerProfiles.count(ProviderProfile::enabled) > 1 || !current.enabled,
                        codexCliAccountService = codexCliAccountService,
                        onSave = onProviderEdited,
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Delete provider",
                enabled = providerProfiles.size > 1 && managedProvider != null,
                onClick = {
                    val current = managedProvider ?: return@ActionIconButton
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
        PanelInfoBox(
            "A connection contains only endpoint, credentials, and provider options. " +
                "Changing the connection immediately reloads its available models below.",
        )
    }
    PanelSection(title = "Main agent model") {
        PanelInfoBox(
            "Select a recognized model available from ${managedProvider?.name ?: "the active provider"}. " +
                "The selected model is applied to the main agent immediately.",
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
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Refresh models",
                enabled = !loadingModels && providerId.isNotBlank(),
                onClick = onRefreshModels,
            )
        }
        if (filteredModels.isEmpty()) {
            PanelEmptyState(
                title = "No selectable models",
                body = "Refresh models or adjust the search and favorites filters for ${managedProvider?.name ?: "the active provider"}.",
            )
        } else {
            ModelSelectionList(
                models = filteredModels,
                selectedModelId = resolvedModel,
                favoriteModels = favoriteModels,
                onSelected = onModelSelected,
                onFavoriteModelsChange = onFavoriteModelsChange,
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

private fun ComposeModalRequester.requestProviderProfileDialog(
    profile: ProviderProfile?,
    canDisable: Boolean,
    codexCliAccountService: CodexCliAccountService?,
    onSave: (ProviderProfile) -> Unit,
) {
    val isNew = profile == null
    request(
        ComposeContentModal(title = if (isNew) "New provider" else "Edit provider") { dismiss ->
            ProviderProfileEditor(
                initial = profile?.toFormState() ?: newProviderFormState(),
                existing = profile,
                canDisable = canDisable,
                codexCliAccountService = codexCliAccountService,
                onCancel = dismiss,
                onSave = { savedProfile ->
                    onSave(savedProfile)
                    dismiss()
                },
            )
        },
    )
}

@Composable
private fun ModelSelectionList(
    models: List<ProviderModelConfig>,
    selectedModelId: String,
    favoriteModels: Set<String>,
    onSelected: (String) -> Unit,
    onFavoriteModelsChange: (Set<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        models.forEach { model ->
            PanelContentCard(modifier = Modifier.clickable { onSelected(model.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = model.id == selectedModelId,
                        onClick = { onSelected(model.id) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.modelDisplayName())
                        if (model.capabilities.isNotEmpty()) {
                            Text(model.capabilities.sorted().joinToString(" · "))
                        }
                    }
                    if (model.id == selectedModelId) {
                        Text("Selected")
                    }
                    ActionIconButton(
                        icon = if (model.id in favoriteModels) Icons.Filled.Star else Icons.Filled.StarBorder,
                        description =
                            if (model.id in favoriteModels) {
                                "Remove ${model.modelDisplayName()} from favorites"
                            } else {
                                "Add ${model.modelDisplayName()} to favorites"
                            },
                        onClick = {
                            onFavoriteModelsChange(
                                if (model.id in favoriteModels) favoriteModels - model.id else favoriteModels + model.id,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderSelectionList(
    providers: List<ProviderProfile>,
    selectedProviderId: String,
    activeProviderId: String,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        providers.forEach { provider ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(provider.id) },
            ) {
                RadioButton(
                    selected = provider.id == selectedProviderId,
                    onClick = { onSelected(provider.id) },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name)
                    Text("${provider.adapter.name} · ${provider.baseUrl}")
                }
                if (provider.id == activeProviderId) {
                    Text("Active")
                }
            }
        }
    }
}

internal fun ProviderProfile.providerDisplayName(): String = "$name ($id)"

internal fun ProviderModelConfig.modelDisplayName(): String =
    if (name != id) {
        "$name ($id)"
    } else {
        id
    }
