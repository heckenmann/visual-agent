@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.settings

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
import de.heckenmann.visualagent.agent.codex.CodexCliAccountService
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.error.ErrorMessageMapper
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
import kotlinx.coroutines.launch

/**
 * Application settings panel for provider/model configuration and runtime preferences.
 *
 * Use cases: UC-0000007, UC-0000008, UC-0000009, UC-0000037, UC-0000038,
 * UC-0000061, UC-0000062, UC-0000064, UC-0000065, UC-0000071.
 */
@Composable
internal fun SettingsPanel(
    config: AppConfigBean,
    llmProvider: LLMProvider,
    providerCatalogService: ProviderCatalogService,
    codexCliAccountService: CodexCliAccountService? = null,
    modalRequester: ComposeModalRequester,
    onSettingsChanged: () -> Unit,
    inFlight: InFlightStateHolder,
    toolEventBus: ToolEventBus,
) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(providerCatalogService.enabledProviders()) }
    var providerProfiles by remember { mutableStateOf(providerCatalogService.listProviders()) }
    var providerId by remember { mutableStateOf(providerCatalogService.activeProviderId()) }
    var selectableModels by remember { mutableStateOf(providerCatalogService.selectableModels(providerId)) }
    var modelId by remember { mutableStateOf(providerCatalogService.activeModelId()) }
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
    var queueFlushMode by remember { mutableStateOf(config.queueFlushMode) }
    var userInstruction by remember { mutableStateOf(config.userModelInstruction) }
    var themeMode by remember { mutableStateOf(config.uiThemeMode) }
    var status by remember { mutableStateOf("Ready") }
    val activeProvider = providers.firstOrNull { it.id == providerId } ?: providers.firstOrNull()
    val managedProvider = providerProfiles.firstOrNull { it.id == providerId }
    val filteredModels = filteredProviderModels(selectableModels, modelSearch, favoritesOnly, favoriteModels)
    val selectableModelIds = selectableModels.map(ProviderModelConfig::id)
    val resolvedModel =
        modelId.takeIf { it in selectableModelIds }
            ?: activeProvider?.defaultModel?.takeIf { it in selectableModelIds }
            ?: selectableModels.firstOrNull()?.id.orEmpty()
    val activeModelCapabilities = selectableModels.firstOrNull { it.id == resolvedModel }?.capabilities.orEmpty()
    val loadLimitValue = loadLimit.toBoundedIntOrNull(MIN_LOAD_LIMIT, MAX_LOAD_LIMIT)
    val maxParallelValue = maxParallelSubAgents.toBoundedIntOrNull(MIN_PARALLEL_SUB_AGENTS, MAX_PARALLEL_SUB_AGENTS)
    val timeoutValue = timeoutSeconds.toBoundedIntOrNull(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
    val canSave =
        canSaveSettings(activeProvider != null, resolvedModel, loadLimitValue, maxParallelValue, timeoutValue)

    /**
     * Reloads the provider list and aligns the form state with the selected provider.
     */
    fun refreshProviderState(selectedProviderId: String = providerId) {
        val refreshed = refreshedProviderSettings(providerCatalogService, selectedProviderId)
        providerProfiles = refreshed.providerProfiles
        providers = refreshed.providers
        providerId = refreshed.providerId
        selectableModels = refreshed.selectableModels
        modelId = refreshed.modelId
    }

    ToolEventRefreshEffect(
        toolEventBus = toolEventBus,
        toolIds = setOf("ui"),
        onRefresh = { refreshProviderState() },
    )

    /** Reloads the selectable model list from the active provider. */
    fun refreshModels() {
        val requestedProviderId = providerId
        if (requestedProviderId.isBlank()) return
        loadingModels = true
        status = "Loading models..."
        scope.launch {
            runCatching {
                val discoveredModels = llmProvider.getModels(requestedProviderId)
                require(discoveredModels.isNotEmpty()) { "Provider did not return any models" }
                providerCatalogService.updateDiscoveredModels(requestedProviderId, discoveredModels)
            }.onSuccess {
                selectableModels = providerCatalogService.selectableModels(requestedProviderId)
                if (modelId !in selectableModels.map(ProviderModelConfig::id)) {
                    modelId =
                        providerCatalogService
                            .getProvider(requestedProviderId)
                            ?.defaultModel
                            ?.takeIf { default -> selectableModels.any { it.id == default } }
                            ?: selectableModels.firstOrNull()?.id.orEmpty()
                }
                status = "Loaded ${selectableModels.size} selectable models."
            }.onFailure { error ->
                selectableModels = providerCatalogService.selectableModels(requestedProviderId)
                status = ProviderErrorMessages.userFacing(error)
            }
            loadingModels = false
        }
    }

    LaunchedEffect(providerId, managedProvider?.adapter, selectableModels.isEmpty()) {
        if (managedProvider?.adapter == ProviderAdapter.CODEX_CLI && selectableModels.isEmpty()) {
            refreshModels()
        }
    }

    /** Loads detailed metadata for the currently selected model. */
    fun refreshModelDetails(modelOverride: String? = null) {
        val requestedProviderId = providerId
        val requestedModel = modelOverride?.trim().orEmpty().ifBlank { resolvedModel }
        if (requestedProviderId.isBlank() || requestedModel.isBlank()) return
        loadingDetails = true
        modelDetails = "Loading model details..."
        scope.launch {
            runCatching { llmProvider.getModelDetails(requestedProviderId, requestedModel) }
                .onSuccess { details -> modelDetails = details.toModelDetailsText() }
                .onFailure { error -> modelDetails = ProviderErrorMessages.userFacing(error) }
            loadingDetails = false
        }
    }

    val settingsLoading = loadingModels || loadingDetails
    LaunchedEffect(settingsLoading) {
        inFlight.setSettingsLoading(settingsLoading)
    }
    val scrollState = rememberScrollState()
    RegisterPanelVerticalScrollbar(scrollState)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
    ) {
        SettingsProviderSection(
            providers = providers,
            providerProfiles = providerProfiles,
            providerId = providerId,
            managedProvider = managedProvider,
            modelSearch = modelSearch,
            favoritesOnly = favoritesOnly,
            favoriteModels = favoriteModels,
            resolvedModel = resolvedModel,
            modelDetails = modelDetails,
            loadingModels = loadingModels,
            loadingDetails = loadingDetails,
            filteredModels = filteredModels,
            modalRequester = modalRequester,
            codexCliAccountService = codexCliAccountService,
            onProviderSelected = { selected ->
                runCatching {
                    val name = activateSelectedProvider(config, providerCatalogService, selected)
                    refreshProviderState(selected)
                    onSettingsChanged()
                    status = "Active provider=$name"
                }.onFailure { error ->
                    status = ProviderErrorMessages.userFacing(error)
                }
            },
            onModelSearchChange = { modelSearch = it },
            onFavoritesOnlyChange = { favoritesOnly = it },
            onFavoriteModelsChange = {
                favoriteModels = it
                config.favoriteModels = it.toList().sorted().toFavoriteModelText()
                config.save()
                status = "Saved favorite models."
            },
            onModelSelected = { selected ->
                modelId = selected
                runCatching {
                    activateMainAgentSelection(config, providerCatalogService, providerId, selected)
                    onSettingsChanged()
                    status = "Active provider=${activeProvider?.name ?: providerId} model=$selected"
                }.onFailure { error ->
                    status = ProviderErrorMessages.userFacing(error)
                }
                refreshModelDetails(selected)
            },
            onRefreshModels = ::refreshModels,
            onRefreshDetails = { refreshModelDetails() },
            onProviderAdded = { profile ->
                providerCatalogService.saveProvider(profile)
                refreshProviderState()
                status = "Saved provider=${profile.name}"
            },
            onProviderEdited = { profile ->
                providerCatalogService.saveProvider(profile)
                refreshProviderState(providerCatalogService.activeProviderId())
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
            queueFlushMode = queueFlushMode,
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
            onQueueFlushModeChange = { queueFlushMode = it },
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
                        config,
                        providerCatalogService,
                        activeProvider?.id ?: providerId,
                        resolvedModel,
                    )
                    applyAndSaveSettings(
                        config,
                        fontSize,
                        contextLength,
                        loadLimitValue,
                        maxParallelValue,
                        timeoutValue,
                        streamingEnabled,
                        thinkingEnabled,
                        autoCompactionEnabled,
                        queueFlushMode,
                        userInstruction,
                    )
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
