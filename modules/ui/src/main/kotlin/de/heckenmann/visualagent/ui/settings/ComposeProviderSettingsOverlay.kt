package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProviderConfiguration
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelScrollbarHost
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar
import de.heckenmann.visualagent.ui.components.settingsDraftActionRow
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.workspace.PanelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Displays a global, draft-based provider and model configuration overlay. */
@Composable
internal fun providerSettingsOverlay(
    settingsPort: SettingsPort,
    providerPort: ProviderPort,
    onSettingsChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var persisted by remember { mutableStateOf(ProviderSettingsDraft()) }
    var draft by remember { mutableStateOf(ProviderSettingsDraft()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading provider settings...") }
    var editingProfile by remember { mutableStateOf<ProviderProfile?>(null) }
    var creatingProfile by remember { mutableStateOf(false) }
    val hasUnsavedChanges = loaded && draft != persisted
    val enabledProviders = draft.providers.filter(ProviderProfile::enabled)
    val selectedProvider = draft.providers.firstOrNull { it.id == draft.providerId }
    val models = selectedProvider?.selectableModels().orEmpty()
    val canSave = draft.providerId.isNotBlank() && draft.modelId.isNotBlank()

    /** Loads the persisted catalog, optionally reporting that local edits were discarded. */
    fun loadPersistedDraft(discardingLocalEdits: Boolean) {
        if (saving || refreshing) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    providerSettingsDraft(settingsPort.snapshotAsync(), providerPort.listProviders())
                }
            }.onSuccess { current ->
                persisted = current
                draft = current
                loaded = true
                status = if (discardingLocalEdits) "Discarded unsaved changes" else "Provider settings loaded"
            }.onFailure { error -> status = error.toUiErrorMessage() }
        }
    }

    /** Saves the provider catalog, main-agent selection, and model favorites as one server operation. */
    fun saveDraft() {
        if (!loaded || saving || !hasUnsavedChanges || !canSave) return
        saving = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val savedUiSettings = settingsPort.snapshot()
                    val nextSettings =
                        draft.conversationSettings.copy(
                            uiThemeMode = savedUiSettings.uiThemeMode,
                            fontSize = savedUiSettings.fontSize,
                            uiScalePercent = savedUiSettings.uiScalePercent,
                            showPanelLabels = savedUiSettings.showPanelLabels,
                            providerId = draft.providerId,
                            modelId = draft.modelId,
                            favoriteModels = draft.favoriteModels.sorted(),
                        )
                    settingsPort.save(
                        nextSettings,
                        ProviderConfiguration(draft.providers, draft.providerId, draft.modelId),
                    )
                }
            }.onSuccess {
                persisted = draft
                status = "Saved provider and model settings"
                onSettingsChanged()
            }.onFailure { error -> status = error.toUiErrorMessage() }
            saving = false
        }
    }

    /** Refreshes only the remote model catalog; the active selection remains a local draft. */
    fun refreshModels() {
        val provider = selectedProvider ?: return
        if (refreshing || saving) return
        refreshing = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { providerPort.discoverModels(provider) } }
                .onSuccess { discovered ->
                    draft = draft.withModels(provider.id, discovered)
                    status = "Loaded ${discovered.size} selectable models"
                }.onFailure { error -> status = error.toUiErrorMessage() }
            refreshing = false
        }
    }

    LaunchedEffect(settingsPort, providerPort) { loadPersistedDraft(discardingLocalEdits = false) }
    if (creatingProfile || editingProfile != null) {
        ProviderProfileEditor(
            initial = editingProfile?.toFormState() ?: newProviderFormState(),
            existing = editingProfile,
            canDisable = enabledProviders.size > 1 || editingProfile?.enabled != true,
            onCancel = {
                creatingProfile = false
                editingProfile = null
            },
            onSave = { profile ->
                draft = draft.upsert(profile)
                creatingProfile = false
                editingProfile = null
                status = "Staged provider ${profile.name}"
            },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        val scrollState = rememberScrollState()
        PanelScrollbarHost(modifier = Modifier.fillMaxWidth().weight(1f)) {
            RegisterPanelVerticalScrollbar(scrollState)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 14.dp),
            ) {
                conversationSettingsSection(
                    settings = draft.conversationSettings,
                    onChange = { conversationSettings -> draft = draft.copy(conversationSettings = conversationSettings) },
                )
                PanelSection(title = "Main agent connection") {
                    PanelDropdownField(
                        label = "Provider",
                        selectedValue = draft.providerId,
                        options = enabledProviders.map { profile -> PanelSelectOption(profile.id, profile.name) },
                        enabled = enabledProviders.isNotEmpty(),
                        onSelected = { providerId ->
                            val nextModels =
                                draft.providers
                                    .firstOrNull { it.id == providerId }
                                    ?.selectableModels()
                                    .orEmpty()
                            draft = draft.copy(providerId = providerId, modelId = nextModels.firstOrNull()?.id.orEmpty())
                        },
                        information =
                            "Selects the connection and credentials used by the main agent. " +
                                "It also changes the available model catalog.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { creatingProfile = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add provider")
                        }
                        OutlinedButton(
                            enabled = selectedProvider != null,
                            onClick = { editingProfile = selectedProvider },
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Edit provider")
                        }
                        OutlinedButton(
                            enabled = draft.providers.size > 1 && selectedProvider != null,
                            onClick = {
                                selectedProvider?.let { profile ->
                                    draft = draft.remove(profile.id)
                                    status = "Staged removal of ${profile.name}"
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Remove provider")
                        }
                    }
                    PanelInfoBox("Provider and credential changes remain local until you save this dialog.")
                    androidx.compose.material3.HorizontalDivider()
                    modelSettingsContent(
                        providerId = draft.providerId,
                        modelId = draft.modelId,
                        models = models,
                        loadingModels = refreshing,
                        modelDetails = "Select a model and save to use it for new agent requests.",
                        favoriteModels = draft.favoriteModels.toList(),
                        onModelSelected = { modelId -> draft = draft.copy(modelId = modelId) },
                        onRefreshModels = ::refreshModels,
                        onFavoriteChanged = { favorite ->
                            draft =
                                draft.copy(
                                    favoriteModels =
                                        draft.favoriteModels.toMutableSet().apply {
                                            if (favorite) add(draft.modelId) else remove(draft.modelId)
                                        },
                                )
                        },
                    )
                }
            }
        }
        androidx.compose.material3.HorizontalDivider()
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

/** Holds the complete local provider edit until the settings overlay is saved or reset. */
internal data class ProviderSettingsDraft(
    val providers: List<ProviderProfile> = emptyList(),
    val providerId: String = "",
    val modelId: String = "",
    val favoriteModels: Set<String> = emptySet(),
    val conversationSettings: SettingsSnapshot = SettingsSnapshot(),
)

private fun providerSettingsDraft(
    settings: SettingsSnapshot,
    providers: List<ProviderProfile>,
): ProviderSettingsDraft =
    ProviderSettingsDraft(
        providers = providers,
        providerId = settings.providerId,
        modelId = settings.modelId,
        favoriteModels = settings.favoriteModels.toSet(),
        conversationSettings = settings,
    )

/** Replaces one staged profile and retains a valid enabled provider/model selection. */
internal fun ProviderSettingsDraft.upsert(profile: ProviderProfile): ProviderSettingsDraft =
    copy(providers = providers.filterNot { it.id == profile.id } + profile).normalizeSelection()

private fun ProviderSettingsDraft.remove(providerId: String): ProviderSettingsDraft =
    copy(providers = providers.filterNot { it.id == providerId }).normalizeSelection()

/** Keeps the draft selection aligned with an enabled provider and one of its selectable models. */
internal fun ProviderSettingsDraft.normalizeSelection(): ProviderSettingsDraft {
    val nextProvider =
        providerId.takeIf { id -> providers.any { it.id == id && it.enabled } }
            ?: providers.firstOrNull(ProviderProfile::enabled)?.id.orEmpty()
    val selectableModels = providers.firstOrNull { it.id == nextProvider }?.selectableModels().orEmpty()
    val nextModel = modelId.takeIf { id -> selectableModels.any { it.id == id } } ?: selectableModels.firstOrNull()?.id.orEmpty()
    return copy(providerId = nextProvider, modelId = nextModel)
}

private fun ProviderSettingsDraft.withModels(
    providerId: String,
    models: List<ProviderModel>,
): ProviderSettingsDraft {
    val updated = providers.map { profile -> if (profile.id == providerId) profile.copy(models = models) else profile }
    val selectedModel = modelId.takeIf { id -> models.any { it.id == id } } ?: models.firstOrNull()?.id.orEmpty()
    return copy(providers = updated, modelId = selectedModel)
}

private fun ProviderProfile.selectableModels(): List<ProviderModel> =
    models.filter { model ->
        model.id !in modelBlacklist &&
            (modelWhitelist.isEmpty() || model.id in modelWhitelist) &&
            model.status != de.heckenmann.visualagent.protocol.ModelStatus.DEPRECATED &&
            model.status != de.heckenmann.visualagent.protocol.ModelStatus.DISABLED
    }
