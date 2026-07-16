@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.error.ErrorMessageMapper
import kotlinx.coroutines.launch

/**
 * Application settings panel for provider/model configuration and runtime
 * preferences.
 *
 * Use cases: UC-0000007, UC-0000008, UC-0000009, UC-0000037, UC-0000038,
 * UC-0000061, UC-0000062, UC-0000064, UC-0000065, UC-0000071.
 *
 * @param config Mutable application configuration
 * @param llmProvider Provider used to refresh model lists and details
 * @param providerCatalogService Provider profile catalog
 * @param modalRequester Modal requester used for destructive confirmations
 * @param onSettingsChanged Callback invoked when provider/theme/font changed
 * @param inFlight In-flight state holder for model refresh indicators
 */
@Composable
internal fun SettingsPanel(
    config: AppConfigBean,
    llmProvider: LLMProvider,
    providerCatalogService: ProviderCatalogService,
    modalRequester: ComposeModalRequester,
    onSettingsChanged: () -> Unit,
    inFlight: InFlightStateHolder,
    toolEventBus: ToolEventBus,
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
    var fontSize by remember { mutableStateOf(config.fontSize.clampFontSize()) }
    var contextLength by remember { mutableStateOf(config.contextLength.coerceIn(MIN_CONTEXT_LENGTH, MAX_CONTEXT_LENGTH)) }
    var loadLimit by remember { mutableStateOf(config.loadLimit.toString()) }
    var maxParallelSubAgents by remember { mutableStateOf(config.maxParallelSubAgents.toString()) }
    var timeoutSeconds by remember { mutableStateOf(config.timeoutSeconds.toString()) }
    var streamingEnabled by remember { mutableStateOf(config.streamingEnabled) }
    var thinkingEnabled by remember { mutableStateOf(config.thinkingEnabled) }
    var autoCompactionEnabled by remember { mutableStateOf(config.autoCompactionEnabled) }
    var userInstruction by remember { mutableStateOf(config.userModelInstruction) }
    var themeMode by remember { mutableStateOf(config.uiThemeMode) }
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
    val activeModelCapabilities = selectableModels.firstOrNull { it.id == resolvedModel }?.capabilities.orEmpty()
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

    ToolEventRefreshEffect(
        toolEventBus = toolEventBus,
        toolIds = setOf("ui"),
        onRefresh = { refreshProviderState() },
    )

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
        SettingsProviderSection(
            providers = providers,
            providerId = providerId,
            activeProvider = activeProvider,
            modelId = modelId,
            modelSelection = modelSelection,
            customModelId = customModelId,
            baseUrl = baseUrl,
            apiKey = apiKey,
            apiKeyVisible = apiKeyVisible,
            modelSearch = modelSearch,
            favoritesOnly = favoritesOnly,
            favoriteModels = favoriteModels,
            resolvedModel = resolvedModel,
            modelDetails = modelDetails,
            loadingModels = loadingModels,
            loadingDetails = loadingDetails,
            filteredModels = filteredModels,
            config = config,
            providerCatalogService = providerCatalogService,
            modalRequester = modalRequester,
            onProviderSelected = { selected -> refreshProviderState(selected) },
            onBaseUrlChange = { baseUrl = it },
            onApiKeyChange = { apiKey = it },
            onApiKeyVisibleToggle = { apiKeyVisible = !apiKeyVisible },
            onModelSearchChange = { modelSearch = it },
            onFavoritesOnlyChange = { favoritesOnly = it },
            onFavoriteModelsChange = {
                favoriteModels = it
                config.favoriteModels = it.toList().sorted().toFavoriteModelText()
                config.save()
                status = "Saved favorite models."
            },
            onModelSelected = { selected ->
                if (selected == CUSTOM_MODEL_ID) {
                    modelId = customModelId
                } else {
                    modelId = selected
                    customModelId = selected
                }
                refreshModelDetails(selected.takeUnless { it == CUSTOM_MODEL_ID })
            },
            onCustomModelChange = {
                customModelId = it
                modelId = it
            },
            onRefreshModels = ::refreshModels,
            onRefreshDetails = { refreshModelDetails() },
            onProviderAdded = { profile ->
                providerCatalogService.saveProvider(profile)
                refreshProviderState(profile.id)
                status = "Saved provider=${profile.name}"
            },
            onProviderEdited = { profile ->
                providerCatalogService.saveProvider(profile)
                mirrorProviderToAppConfig(config, profile)
                config.save()
                refreshProviderState(profile.id)
                onSettingsChanged()
                status = "Saved provider=${profile.name}"
            },
            onProviderDeleted = { current ->
                if (providerCatalogService.deleteProvider(current.id)) {
                    refreshProviderState(providerCatalogService.activeProviderId())
                    config.llmProvider = providerCatalogService.activeProviderId()
                    config.save()
                    onSettingsChanged()
                    status = "Deleted provider=${current.name}"
                }
            },
        )

        SettingsExecutionAndAppearanceSection(
            config = config,
            contextLength = contextLength,
            loadLimit = loadLimit,
            maxParallelSubAgents = maxParallelSubAgents,
            timeoutSeconds = timeoutSeconds,
            streamingEnabled = streamingEnabled,
            thinkingEnabled = thinkingEnabled,
            autoCompactionEnabled = autoCompactionEnabled,
            userInstruction = userInstruction,
            fontSize = fontSize,
            themeMode = themeMode,
            modelCapabilities = activeModelCapabilities,
            onContextLengthChange = { contextLength = it },
            onLoadLimitChange = { loadLimit = it },
            onMaxParallelChange = { maxParallelSubAgents = it },
            onTimeoutChange = { timeoutSeconds = it },
            onStreamingChange = { streamingEnabled = it },
            onThinkingChange = { thinkingEnabled = it },
            onCompactionChange = { autoCompactionEnabled = it },
            onUserInstructionChange = { userInstruction = it },
            onFontSizeChange = { newSize ->
                fontSize = newSize
                config.fontSize = newSize
                onSettingsChanged()
            },
            onThemeModeChange = { newMode ->
                themeMode = newMode
                config.uiThemeMode = newMode
                config.save()
                onSettingsChanged()
            },
        )

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
                    val userError = ErrorMessageMapper.map(it)
                    status = "${userError.summary}: ${userError.detail}"
                }
            },
        )
        PanelStatus(status)
    }
}

internal const val CUSTOM_MODEL_ID = "__custom_model__"
