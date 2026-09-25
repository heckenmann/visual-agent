package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.MainAgentMemoryPort
import de.heckenmann.visualagent.protocol.MainAgentMemoryUpdate
import de.heckenmann.visualagent.protocol.ProviderConfiguration
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.ui.components.PanelScrollbarHost
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
    mainAgentMemoryPort: MainAgentMemoryPort,
    providerPort: ProviderPort,
    onSettingsChanged: () -> Unit,
    onRunOnboarding: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var persisted by remember { mutableStateOf(ProviderSettingsDraft()) }
    var draft by remember { mutableStateOf(ProviderSettingsDraft()) }
    var persistedMemory by remember { mutableStateOf(initialMainAgentMemorySnapshot()) }
    var draftMemory by remember { mutableStateOf("") }
    var selectedContextLimit by remember { mutableStateOf<Int?>(null) }
    var modelSelectionRevision by remember { mutableStateOf(0) }
    var adoptModelContextLimit by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshGeneration by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("Loading provider settings...") }
    var editingProfile by remember { mutableStateOf<ProviderProfile?>(null) }
    var creatingProfile by remember { mutableStateOf(false) }
    val hasUnsavedChanges = loaded && (draft != persisted || draftMemory != persistedMemory.content)
    val enabledProviders = draft.providers.filter(ProviderProfile::enabled)
    val selectedProvider = draft.providers.firstOrNull { it.id == draft.providerId }
    val models = selectedProvider?.selectableModels().orEmpty()
    val selectedModel = models.firstOrNull { it.id == draft.modelId }
    val providerConfigurationChanged =
        draft.providers != persisted.providers || draft.providerId != persisted.providerId || draft.modelId != persisted.modelId
    val canSave =
        !refreshing &&
            draft.providerId.isNotBlank() &&
            draft.modelId.isNotBlank() &&
            (!providerConfigurationChanged || models.any { it.id == draft.modelId })
    val memoryFitsLimit = draftMemory.codePointCount(0, draftMemory.length) <= draft.conversationSettings.maxMainAgentMemoryChars

    modelContextLimitEffect(
        loaded = loaded,
        providerId = draft.providerId,
        modelId = draft.modelId,
        catalogLimit = selectedModel?.contextLimit,
        selectionRevision = modelSelectionRevision,
        adoptLimit = adoptModelContextLimit,
        providerPort = providerPort,
        onLimitResolved = { selectedContextLimit = it },
        onLimitApplied = { limit, adopt -> draft = draft.withModelContextLimit(limit, adopt) },
        onSelectionResolved = { revision -> if (modelSelectionRevision == revision) adoptModelContextLimit = false },
    )

    /** Loads the persisted catalog, optionally reporting that local edits were discarded. */
    fun loadPersistedDraft(discardingLocalEdits: Boolean) {
        if (saving) return
        refreshGeneration++
        refreshing = false
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    providerSettingsDraft(settingsPort.snapshotAsync(), providerPort.listProviders()) to mainAgentMemoryPort.snapshot()
                }
            }.onSuccess { (current, memory) ->
                persisted = current
                draft = current
                persistedMemory = memory
                draftMemory = memory.content
                loaded = true
                status = if (discardingLocalEdits) "Discarded unsaved changes" else "Provider settings loaded"
            }.onFailure { error -> status = error.toUiErrorMessage() }
        }
    }

    /** Saves the provider catalog, main-agent selection, and model favorites as one server operation. */
    fun saveDraft() {
        if (!loaded || saving || !hasUnsavedChanges || !canSave) return
        if (!memoryFitsLimit) {
            status = "Reduce main-agent memory before saving its lower limit"
            return
        }
        if (
            draftMemory != persistedMemory.content &&
            draft.conversationSettings.maxMainAgentMemoryChars < persistedMemory.limit
        ) {
            status = "Save the reduced memory before lowering its limit"
            return
        }
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
                        if (providerConfigurationChanged) {
                            ProviderConfiguration(draft.providers, draft.providerId, draft.modelId)
                        } else {
                            null
                        },
                    )
                    if (draftMemory != persistedMemory.content) {
                        mainAgentMemoryPort.replace(draftMemory, persistedMemory.revision)
                    } else {
                        null
                    }
                }
            }.onSuccess { memoryUpdate ->
                persisted = draft
                when (memoryUpdate) {
                    is MainAgentMemoryUpdate.Saved -> {
                        persistedMemory = memoryUpdate.snapshot
                        draftMemory = memoryUpdate.snapshot.content
                        status = "Saved provider, model, and main-agent memory settings"
                    }
                    is MainAgentMemoryUpdate.Conflict -> {
                        persistedMemory = memoryUpdate.snapshot
                        draftMemory = memoryUpdate.snapshot.content
                        status = "Memory changed elsewhere; loaded its latest version"
                    }
                    null -> status = "Saved provider and model settings"
                }
                onSettingsChanged()
            }.onFailure { error -> status = error.toUiErrorMessage() }
            saving = false
        }
    }

    /** Refreshes only the remote model catalog; the active selection remains a local draft. */
    fun refreshModels(
        provider: ProviderProfile? = selectedProvider,
        preferredModelId: String? = null,
    ) {
        if (provider == null) return
        if (saving) return
        val generation = ++refreshGeneration
        refreshing = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { providerPort.discoverModels(provider) } }
                .onSuccess { discovered ->
                    if (generation == refreshGeneration) {
                        draft = draft.withModels(provider.id, discovered, preferredModelId)
                        status = "Loaded ${discovered.size} selectable models"
                    }
                }.onFailure { error ->
                    if (generation == refreshGeneration) status = error.toUiErrorMessage()
                }
            if (generation == refreshGeneration) refreshing = false
        }
    }

    /** Stages a model selection and adopts its reported context capacity. */
    fun selectModel(
        providerId: String,
        modelId: String,
    ) {
        adoptModelContextLimit = true
        modelSelectionRevision++
        draft = draft.copy(providerId = providerId, modelId = modelId)
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
                refreshGeneration++
                refreshing = false
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
                mainAgentMemorySection(
                    content = draftMemory,
                    snapshot = persistedMemory,
                    limit = draft.conversationSettings.maxMainAgentMemoryChars,
                    onContentChange = { draftMemory = it },
                )
                providerConnectionSection(
                    draft = draft,
                    enabledProviders = enabledProviders,
                    selectedProvider = selectedProvider,
                    models = models,
                    contextLength = draft.conversationSettings.contextLength,
                    contextLimit = selectedContextLimit,
                    refreshing = refreshing,
                    onRunOnboarding = onRunOnboarding,
                    onProviderSelected = { providerId ->
                        val nextModels =
                            draft.providers
                                .firstOrNull { it.id == providerId }
                                ?.selectableModels()
                                .orEmpty()
                        val previous =
                            if (providerId == persisted.providerId) {
                                persisted.modelId
                            } else {
                                draft.providers.firstOrNull { it.id == providerId }?.defaultModel
                            }
                        val modelId = previous?.takeIf(String::isNotBlank) ?: nextModels.firstOrNull()?.id.orEmpty()
                        selectModel(providerId, modelId)
                        refreshModels(draft.providers.firstOrNull { it.id == providerId }, previous)
                    },
                    onAddProvider = { creatingProfile = true },
                    onEditProvider = { editingProfile = selectedProvider },
                    onRemoveProvider = {
                        selectedProvider?.let { profile ->
                            refreshGeneration++
                            refreshing = false
                            draft = draft.remove(profile.id)
                            status = "Staged removal of ${profile.name}"
                        }
                    },
                    onModelSelected = { modelId -> selectModel(draft.providerId, modelId) },
                    onContextLengthChanged = { value ->
                        draft = draft.copy(conversationSettings = draft.conversationSettings.copy(contextLength = value))
                    },
                    onRefreshModels = { refreshModels() },
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
        androidx.compose.material3.HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            settingsDraftActionRow(
                hasUnsavedChanges = hasUnsavedChanges,
                saving = saving,
                canSave = canSave,
                onReset = { loadPersistedDraft(discardingLocalEdits = true) },
                onSave = ::saveDraft,
            )
            PanelStatus(status)
        }
    }
}
