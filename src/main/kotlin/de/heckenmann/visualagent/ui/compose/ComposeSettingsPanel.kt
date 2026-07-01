@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfig
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SettingsPanel(
    config: AppConfig,
    llmProvider: LLMProvider,
    providerCatalogService: ProviderCatalogService,
    modalRequester: ComposeModalRequester,
    onSettingsChanged: () -> Unit,
    inFlight: InFlightStateHolder,
) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(providerCatalogService.enabledProviders()) }
    var providerId by remember { mutableStateOf(providerCatalogService.activeProviderId()) }
    var selectableModels by remember { mutableStateOf(providerCatalogService.selectableModels(providerId)) }
    var modelId by remember {
        mutableStateOf(providerCatalogService.getProvider(providerId)?.defaultModel.orEmpty())
    }
    var customModelId by remember { mutableStateOf(modelId) }
    var baseUrl by remember { mutableStateOf(providerCatalogService.getProvider(providerId)?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf(providerCatalogService.getProvider(providerId)?.apiKey.orEmpty()) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var favoriteModels by remember { mutableStateOf(config.favoriteModels.toFavoriteModelSet()) }
    var modelDetails by remember { mutableStateOf("Select a model to load details.") }
    var loadingModels by remember { mutableStateOf(false) }
    var loadingDetails by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(config.theme.takeIf { it in SUPPORTED_SETTINGS_THEMES } ?: DEFAULT_SETTINGS_THEME) }
    var fontSize by remember { mutableStateOf(config.fontSize.clampFontSize()) }
    var contextLength by remember { mutableStateOf(config.contextLength.coerceIn(MIN_CONTEXT_LENGTH, MAX_CONTEXT_LENGTH)) }
    var loadLimit by remember { mutableStateOf(config.loadLimit.toString()) }
    var maxParallelSubAgents by remember { mutableStateOf(config.maxParallelSubAgents.toString()) }
    var timeoutSeconds by remember { mutableStateOf(config.timeoutSeconds.toString()) }
    var streamingEnabled by remember { mutableStateOf(config.streamingEnabled) }
    var thinkingEnabled by remember { mutableStateOf(config.thinkingEnabled) }
    var autoCompactionEnabled by remember { mutableStateOf(config.autoCompactionEnabled) }
    var userInstruction by remember { mutableStateOf(config.userModelInstruction) }
    var status by remember { mutableStateOf("Ready") }
    val activeProvider = providers.firstOrNull { it.id == providerId } ?: providers.firstOrNull()
    val filteredModels =
        selectableModels.filter { model ->
            val matchesSearch = model.id.contains(modelSearch, ignoreCase = true) || model.name.contains(modelSearch, ignoreCase = true)
            val matchesFavorite = !favoritesOnly || model.id in favoriteModels
            matchesSearch && matchesFavorite
        }
    val selectableModelIds = selectableModels.map(ProviderModelConfig::id)
    val modelSelection = if (modelId in selectableModelIds) modelId else CUSTOM_MODEL_ID
    val resolvedModel =
        when (modelSelection) {
            CUSTOM_MODEL_ID -> customModelId.trim()
            else -> modelSelection
        }.ifBlank {
            activeProvider?.defaultModel.orEmpty().ifBlank { selectableModels.firstOrNull()?.id.orEmpty() }
        }
    val loadLimitValue = loadLimit.toBoundedIntOrNull(MIN_LOAD_LIMIT, MAX_LOAD_LIMIT)
    val maxParallelValue = maxParallelSubAgents.toBoundedIntOrNull(MIN_PARALLEL_SUB_AGENTS, MAX_PARALLEL_SUB_AGENTS)
    val timeoutValue = timeoutSeconds.toBoundedIntOrNull(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
    val canSave =
        activeProvider != null &&
            resolvedModel.isNotBlank() &&
            baseUrl.isNotBlank() &&
            loadLimitValue != null &&
            maxParallelValue != null &&
            timeoutValue != null

    /**
     * Reloads the provider list and aligns the form state with the selected provider.
     *
     * @param selectedProviderId Provider ID to activate; falls back to the first enabled provider
     *   when the requested ID is not present
     */
    fun refreshProviderState(selectedProviderId: String = providerId) {
        providers = providerCatalogService.enabledProviders()
        providerId = selectedProviderId.takeIf { id -> providers.any { it.id == id } } ?: providers.firstOrNull()?.id.orEmpty()
        val profile = providerCatalogService.getProvider(providerId)
        selectableModels = providerCatalogService.selectableModels(providerId)
        modelId = profile?.defaultModel?.takeIf(String::isNotBlank) ?: selectableModels.firstOrNull()?.id.orEmpty()
        customModelId = modelId
        baseUrl = profile?.baseUrl.orEmpty()
        apiKey = profile?.apiKey.orEmpty()
    }

    /**
     * Reloads the selectable model list from the active provider.
     *
     * Updates `selectableModels` and resets `modelId` if the previously selected model is no
     * longer available. Safe to call when no provider is selected — returns immediately.
     */
    fun refreshModels() {
        val requestedProviderId = providerId
        if (requestedProviderId.isBlank()) return
        loadingModels = true
        status = "Loading models..."
        scope.launch {
            runCatching { llmProvider.getModels(requestedProviderId) }
                .onSuccess {
                    selectableModels = providerCatalogService.selectableModels(requestedProviderId)
                    if (modelId !in selectableModels.map(ProviderModelConfig::id)) {
                        modelId =
                            providerCatalogService.getProvider(requestedProviderId)?.defaultModel?.takeIf(String::isNotBlank)
                                ?: selectableModels.firstOrNull()?.id.orEmpty()
                        customModelId = modelId
                    }
                    status = "Loaded ${selectableModels.size} selectable models."
                }.onFailure { error ->
                    selectableModels = providerCatalogService.selectableModels(requestedProviderId)
                    status = ProviderErrorMessages.userFacing(error)
                }
            loadingModels = false
        }
    }

    /**
     * Loads detailed metadata for the currently selected model.
     *
     * @param modelOverride Optional explicit model ID; falls back to the resolved model when blank
     */
    fun refreshModelDetails(modelOverride: String? = null) {
        val requestedProviderId = providerId
        val requestedModel = modelOverride?.trim().orEmpty().ifBlank { resolvedModel }
        if (requestedProviderId.isBlank() || requestedModel.isBlank()) return
        loadingDetails = true
        modelDetails = "Loading model details..."
        scope.launch {
            runCatching { llmProvider.getModelDetails(requestedProviderId, requestedModel) }
                .onSuccess { details ->
                    modelDetails = details.toModelDetailsText()
                }.onFailure { error ->
                    modelDetails = ProviderErrorMessages.userFacing(error)
                }
            loadingDetails = false
        }
    }

    val settingsLoading = loadingModels || loadingDetails
    LaunchedEffect(settingsLoading) {
        inFlight.setSettingsLoading(settingsLoading)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        PanelSection(title = "Provider and model") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelDropdownField(
                    label = "Provider",
                    selectedValue = providerId,
                    options = providers.map { option -> PanelSelectOption(option.id, option.providerDisplayName()) },
                    onSelected = { selected -> refreshProviderState(selected) },
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
                                        providerCatalogService.saveProvider(profile)
                                        refreshProviderState(profile.id)
                                        status = "Saved provider=${profile.name}"
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
                                        providerCatalogService.saveProvider(profile)
                                        mirrorProviderToAppConfig(config, profile)
                                        config.save()
                                        refreshProviderState(profile.id)
                                        onSettingsChanged()
                                        status = "Saved provider=${profile.name}"
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
                                if (providerCatalogService.deleteProvider(current.id)) {
                                    refreshProviderState(providerCatalogService.activeProviderId())
                                    config.llmProvider = providerCatalogService.activeProviderId()
                                    config.save()
                                    onSettingsChanged()
                                    status = "Deleted provider=${current.name}"
                                }
                            },
                        )
                    },
                )
            }
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    ActionIconButton(
                        icon = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        description = if (apiKeyVisible) "Hide API key" else "Show API key",
                        onClick = { apiKeyVisible = !apiKeyVisible },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = modelSearch,
                    onValueChange = { modelSearch = it },
                    label = { Text("Search models") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                PanelCheckbox(label = "Favorites", checked = favoritesOnly, onCheckedChange = { favoritesOnly = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelDropdownField(
                    label = "Model",
                    selectedValue = modelSelection,
                    options =
                        filteredModels.map { model -> PanelSelectOption(model.id, model.modelDisplayName()) } +
                            PanelSelectOption(CUSTOM_MODEL_ID, "Custom model ID"),
                    onSelected = { selected ->
                        if (selected == CUSTOM_MODEL_ID) {
                            modelId = customModelId
                        } else {
                            modelId = selected
                            customModelId = selected
                        }
                        refreshModelDetails(selected.takeUnless { it == CUSTOM_MODEL_ID })
                    },
                    modifier = Modifier.weight(1f),
                )
                ActionIconButton(
                    icon = if (resolvedModel in favoriteModels) Icons.Filled.Star else Icons.Filled.StarBorder,
                    description = if (resolvedModel in favoriteModels) "Remove model favorite" else "Add model favorite",
                    enabled = resolvedModel.isNotBlank(),
                    onClick = {
                        favoriteModels =
                            if (resolvedModel in favoriteModels) favoriteModels - resolvedModel else favoriteModels + resolvedModel
                        config.favoriteModels = favoriteModels.toList().sorted().toFavoriteModelText()
                        config.save()
                        status = "Saved favorite models."
                    },
                )
                ActionIconButton(
                    icon = Icons.Filled.Refresh,
                    description = "Refresh models",
                    enabled = !loadingModels && providerId.isNotBlank(),
                    onClick = ::refreshModels,
                )
            }
            if (modelSelection == CUSTOM_MODEL_ID) {
                OutlinedTextField(
                    value = customModelId,
                    onValueChange = {
                        customModelId = it
                        modelId = it
                    },
                    label = { Text("Custom model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Refresh model details",
                enabled = !loadingDetails && resolvedModel.isNotBlank(),
                onClick = { refreshModelDetails() },
            )
            PanelInfoBox(modelDetails)
        }

        PanelSection(title = "Execution") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Context length", color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = contextLength.toFloat(),
                        onValueChange = {
                            val snapped = ((it / 1024f).roundToInt() * 1024).coerceIn(MIN_CONTEXT_LENGTH, MAX_CONTEXT_LENGTH)
                            contextLength = snapped
                        },
                        valueRange = MIN_CONTEXT_LENGTH.toFloat()..MAX_CONTEXT_LENGTH.toFloat(),
                        steps = ((MAX_CONTEXT_LENGTH - MIN_CONTEXT_LENGTH) / 1024) - 1,
                    )
                }
                Text("$contextLength", color = Color(0xFFBD93F9), modifier = Modifier.width(72.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                NumericPanelField(
                    label = "Startup history",
                    value = loadLimit,
                    onValueChange = { loadLimit = it },
                    modifier = Modifier.weight(1f),
                )
                NumericPanelField(
                    label = "Parallel agents",
                    value = maxParallelSubAgents,
                    onValueChange = { maxParallelSubAgents = it },
                    modifier = Modifier.weight(1f),
                )
                NumericPanelField(
                    label = "Timeout sec",
                    value = timeoutSeconds,
                    onValueChange = { timeoutSeconds = it },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelCheckbox(label = "Stream", checked = streamingEnabled, onCheckedChange = { streamingEnabled = it })
                PanelCheckbox(label = "Reasoning", checked = thinkingEnabled, onCheckedChange = { thinkingEnabled = it })
                PanelCheckbox(label = "Compaction", checked = autoCompactionEnabled, onCheckedChange = { autoCompactionEnabled = it })
            }
            OutlinedTextField(
                value = userInstruction,
                onValueChange = { userInstruction = it },
                label = { Text("Model instruction") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PanelSection(title = "Appearance") {
            PanelDropdownField(
                label = "Theme",
                selectedValue = theme,
                options = SUPPORTED_SETTINGS_THEMES.map { PanelSelectOption(it, it) },
                onSelected = { theme = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Font size", color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = {
                            fontSize = it.roundToInt().clampFontSize()
                            config.fontSize = fontSize
                            onSettingsChanged()
                        },
                        valueRange = MIN_SETTINGS_FONT_SIZE.toFloat()..MAX_SETTINGS_FONT_SIZE.toFloat(),
                        steps = MAX_SETTINGS_FONT_SIZE - MIN_SETTINGS_FONT_SIZE - 1,
                    )
                }
                Text("$fontSize px", color = Color(0xFFBD93F9), modifier = Modifier.width(48.dp))
            }
        }

        ActionIconButton(
            icon = Icons.Filled.Save,
            description = "Save settings",
            enabled = canSave,
            onClick = {
                runCatching {
                    saveSessionSettings(
                        config = config,
                        providerCatalog = providerCatalogService,
                        providerId = activeProvider?.id ?: providerId,
                        modelId = resolvedModel,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                    )
                    config.theme = theme
                    config.fontSize = fontSize.clampFontSize()
                    config.contextLength = contextLength.coerceIn(MIN_CONTEXT_LENGTH, MAX_CONTEXT_LENGTH)
                    config.loadLimit = loadLimitValue ?: config.loadLimit
                    config.maxParallelSubAgents = maxParallelValue ?: config.maxParallelSubAgents
                    config.timeoutSeconds = timeoutValue ?: config.timeoutSeconds
                    config.streamingEnabled = streamingEnabled
                    config.thinkingEnabled = thinkingEnabled
                    config.autoCompactionEnabled = autoCompactionEnabled
                    config.userModelInstruction = userInstruction
                    config.save()
                    refreshProviderState(activeProvider?.id ?: providerId)
                    onSettingsChanged()
                }.onSuccess {
                    status = "Saved provider=${activeProvider?.name ?: providerId} model=$resolvedModel"
                }.onFailure {
                    status = "Save failed: ${it.message}"
                }
            },
        )
        PanelStatus(status)
    }
}

@Composable
private fun ProviderProfileEditor(
    initial: ProviderProfileFormState,
    existing: ProviderProfile?,
    canDisable: Boolean,
    onCancel: () -> Unit,
    onSave: (ProviderProfile) -> Unit,
) {
    var state by remember { mutableStateOf(initial) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    val validation = state.validationError()
    Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.id,
                onValueChange = { state = state.copy(id = it) },
                label = { Text("Provider ID") },
                enabled = existing == null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = { state = state.copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        PanelDropdownField(
            label = "Adapter",
            selectedValue = state.adapter.name,
            options = ProviderAdapter.entries.map { PanelSelectOption(it.name, it.name) },
            onSelected = { state = state.copy(adapter = ProviderAdapter.valueOf(it)) },
        )
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = { state = state.copy(baseUrl = it) },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = { state = state.copy(apiKey = it) },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                ActionIconButton(
                    icon = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    description = if (apiKeyVisible) "Hide API key" else "Show API key",
                    onClick = { apiKeyVisible = !apiKeyVisible },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.defaultModel,
                onValueChange = { state = state.copy(defaultModel = it) },
                label = { Text("Default model") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PanelCheckbox(
                label = "Enabled",
                checked = state.enabled,
                enabled = canDisable,
                onCheckedChange = { state = state.copy(enabled = it) },
            )
        }
        OutlinedTextField(
            value = state.optionsText,
            onValueChange = { state = state.copy(optionsText = it) },
            label = { Text("Provider options") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.modelsText,
            onValueChange = { state = state.copy(modelsText = it) },
            label = { Text("Models") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.whitelistText,
                onValueChange = { state = state.copy(whitelistText = it) },
                label = { Text("Model whitelist") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.blacklistText,
                onValueChange = { state = state.copy(blacklistText = it) },
                label = { Text("Model blacklist") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (validation != null) {
            Text(validation, color = Color(0xFFFFB86C), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            ActionIconButton(icon = Icons.Filled.Close, description = "Cancel", onClick = onCancel)
            ActionIconButton(
                icon = Icons.Filled.Check,
                description = "Save provider",
                enabled = validation == null,
                onClick = { onSave(state.toProviderProfile(existing)) },
            )
        }
    }
}

private fun ProviderProfile.providerDisplayName(): String = "$name ($id)"

private fun ProviderModelConfig.modelDisplayName(): String =
    if (name != id) {
        "$name ($id)"
    } else {
        id
    }

private fun ShowResponse.toModelDetailsText(): String =
    buildString {
        appendLine("Model: $model")
        appendLine("Modified: ${modifiedAt.ifBlank { "unknown" }}")
        details?.let { metadata ->
            appendLine("Family: ${metadata.family ?: "unknown"}")
            appendLine("Size: ${metadata.parameterSize ?: "unknown"}")
            appendLine("Format: ${metadata.format ?: "unknown"}")
            appendLine("Quantization: ${metadata.quantizationLevel ?: "unknown"}")
        }
    }.trimEnd()

private const val CUSTOM_MODEL_ID = "__custom_model__"
private const val DEFAULT_SETTINGS_THEME = "Dracula"
