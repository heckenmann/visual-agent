package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.modal.requestConfirmation
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import de.heckenmann.visualagent.ui.workspace.PanelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Renders provider, runtime, and appearance settings through the neutral protocol ports. */
@Suppress("FunctionName")
@Composable
internal fun SettingsPanel(
    settingsPort: SettingsPort,
    providerPort: ProviderPort,
    modalRequester: ComposeModalRequester,
    onSettingsChanged: () -> Unit,
    inFlight: InFlightStateHolder,
    @Suppress("UNUSED_PARAMETER") activityPort: ActivityPort,
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(SettingsSnapshot()) }
    var snapshotLoaded by remember { mutableStateOf(false) }
    var providers by remember { mutableStateOf(providerPort.listProviders()) }
    var providerId by remember { mutableStateOf(providerPort.activeProviderId()) }
    var modelId by remember { mutableStateOf(providerPort.activeModelId()) }
    var models by remember { mutableStateOf(providerPort.selectableModels(providerId)) }
    var modelDetails by remember { mutableStateOf("Select a model to load details.") }
    var loadingModels by remember { mutableStateOf(false) }
    var loadingDetails by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    val enabledProviders = providers.filter(ProviderProfile::enabled)
    val selectedProvider = providers.firstOrNull { it.id == providerId }
    val canSaveSelection = providerId.isNotBlank() && modelId.isNotBlank()

    LaunchedEffect(settingsPort) {
        snapshot = settingsPort.snapshotAsync()
        snapshotLoaded = true
    }

    /** Reloads provider profiles and selects a valid model after a catalog change. */
    fun reloadProviderState(preferredProviderId: String = providerId) {
        scope.launch {
            val loaded =
                withContext(Dispatchers.IO) {
                    readProviderSettings(providerPort, preferredProviderId)
                }
            providers = loaded.providers
            providerId = loaded.providerId
            modelId = loaded.modelId
            models = loaded.models
        }
    }

    DisposableEffect(settingsPort, providerPort) {
        val settingsHandle =
            settingsPort.addChangeListener { next ->
                scope.launch {
                    snapshot = next
                    snapshotLoaded = true
                }
            }
        val providerHandle = providerPort.addChangeListener { reloadProviderState() }
        onDispose {
            settingsHandle.close()
            providerHandle.close()
        }
    }
    LaunchedEffect(loadingModels, loadingDetails) {
        inFlight.setSettingsLoading(loadingModels || loadingDetails)
    }

    /** Persists the complete settings snapshot through the application server. */
    fun saveSettings() {
        if (!snapshotLoaded) {
            status = "Settings are still loading."
            return
        }
        if (!canSaveSelection) {
            status = "Select a provider model before saving."
            return
        }
        val next = snapshot.copy(providerId = providerId, modelId = modelId)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    settingsPort.save(next)
                    providerPort.setActiveSelection(providerId, modelId)
                }
            }.onSuccess {
                snapshot = next
                onSettingsChanged()
                status = "Saved settings"
            }.onFailure { status = it.toUiErrorMessage() }
        }
    }

    /** Persists only the active provider/model selection. */
    fun saveSelection() {
        if (!snapshotLoaded) {
            status = "Settings are still loading."
            return
        }
        if (!canSaveSelection) {
            status = "Select a provider model before saving."
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { providerPort.setActiveSelection(providerId, modelId) }
            }.onSuccess {
                snapshot = snapshot.copy(providerId = providerId, modelId = modelId)
                onSettingsChanged()
                status = "Saved provider and model"
            }.onFailure { status = it.toUiErrorMessage() }
        }
    }

    /** Refreshes the selected provider's configured or discovered models without blocking the UI thread. */
    fun refreshModels() {
        if (providerId.isBlank() || loadingModels) return
        loadingModels = true
        status = "Loading models..."
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { providerPort.refreshModels(providerId) } }
                .onSuccess { discovered ->
                    models = discovered
                    providers = withContext(Dispatchers.IO) { providerPort.listProviders() }
                    modelId = modelId.takeIf { selected -> discovered.any { it.id == selected } } ?: discovered.firstOrNull()?.id.orEmpty()
                    status = "Loaded ${discovered.size} selectable models"
                }.onFailure { status = it.toUiErrorMessage() }
            loadingModels = false
        }
    }

    LaunchedEffect(providerId, modelId) {
        if (providerId.isBlank() || modelId.isBlank()) return@LaunchedEffect
        loadingDetails = true
        modelDetails = "Loading model details..."
        runCatching { withContext(Dispatchers.IO) { providerPort.modelDetails(providerId, modelId) } }
            .onSuccess { details ->
                modelDetails =
                    buildString {
                        appendLine("Model: ${details.model}")
                        appendLine("Modified: ${details.modifiedAt.ifBlank { "unknown" }}")
                        appendLine("Family: ${details.family ?: "unknown"}")
                        appendLine("Size: ${details.parameterSize ?: "unknown"}")
                        appendLine("Format: ${details.format ?: "unknown"}")
                        append("Quantization: ${details.quantizationLevel ?: "unknown"}")
                    }
            }.onFailure { modelDetails = it.toUiErrorMessage() }
        loadingDetails = false
    }

    /** Persists a provider profile edited in the modal dialog. */
    val saveProvider: (ProviderProfile) -> Unit = { profile ->
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { providerPort.saveProvider(profile) } }
                .onSuccess {
                    reloadProviderState(profile.id)
                    status = "Saved provider ${profile.name}"
                }.onFailure { status = it.toUiErrorMessage() }
        }
    }

    /** Deletes a provider profile through the application server. */
    val deleteProvider: (ProviderProfile) -> Unit = { profile ->
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { providerPort.deleteProvider(profile.id) } }
                .onSuccess {
                    reloadProviderState()
                    status = "Deleted provider ${profile.name}"
                }.onFailure { status = it.toUiErrorMessage() }
        }
    }

    val scrollState = rememberScrollState()
    RegisterPanelVerticalScrollbar(scrollState)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
    ) {
        PanelSection(title = "Provider connections") {
            PanelDropdownField(
                label = "Provider",
                selectedValue = providerId,
                options = enabledProviders.map { PanelSelectOption(it.id, it.name) },
                onSelected = {
                    providerId = it
                    modelId = ""
                    reloadProviderState(it)
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIconButton(
                    icon = Icons.Filled.Add,
                    description = "Add provider",
                    onClick = { modalRequester.requestProviderProfileDialog(null, true, saveProvider) },
                )
                ActionIconButton(
                    icon = Icons.Filled.Edit,
                    description = "Edit provider",
                    enabled = selectedProvider != null,
                    onClick = {
                        selectedProvider?.let { profile ->
                            modalRequester.requestProviderProfileDialog(
                                profile,
                                enabledProviders.size > 1 || !profile.enabled,
                                saveProvider,
                            )
                        }
                    },
                )
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    description = "Delete provider",
                    enabled = providers.size > 1 && selectedProvider != null,
                    onClick = {
                        selectedProvider?.let { profile ->
                            modalRequester.requestConfirmation(
                                ComposeConfirmationModal(
                                    title = "Delete provider?",
                                    message = "Delete '${profile.name}' from the provider catalog.",
                                    confirmDescription = "Delete provider",
                                    onConfirm = { deleteProvider(profile) },
                                ),
                            )
                        }
                    },
                )
            }
            PanelInfoBox("Configure endpoint and credentials with Add or Edit. Changes are persisted by the application server.")
        }
        ModelSettingsSection(
            providerId = providerId,
            modelId = modelId,
            models = models,
            loadingModels = loadingModels,
            modelDetails = modelDetails,
            favoriteModels = snapshot.favoriteModels,
            snapshotLoaded = snapshotLoaded,
            canSaveSelection = canSaveSelection,
            onModelSelected = { modelId = it },
            onRefreshModels = ::refreshModels,
            onFavoriteChanged = { favorite ->
                val favorites = snapshot.favoriteModels.toMutableSet().apply { if (favorite) add(modelId) else remove(modelId) }
                snapshot = snapshot.copy(favoriteModels = favorites.sorted())
            },
            onSaveSelection = ::saveSelection,
        )
        RuntimeSettingsSection(snapshot) { snapshot = it }
        AppearanceSettingsSection(snapshot) { snapshot = it }
        ActionIconButton(
            icon = Icons.Filled.Save,
            description = "Save settings",
            enabled = snapshotLoaded && canSaveSelection,
            onClick = ::saveSettings,
        )
        PanelStatus(status)
    }
}
