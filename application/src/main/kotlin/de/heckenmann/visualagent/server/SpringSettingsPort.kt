package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderConfiguration
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryStore
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.ThemeMode
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import de.heckenmann.visualagent.config.ThemeMode as ApplicationThemeMode

/** Maps the persisted application settings to the neutral [SettingsPort]. */
@Component
class SpringSettingsPort(
    private val config: AppConfigBean,
    private val providerCatalog: ProviderCatalogService,
    private val mainAgentMemoryStore: MainAgentLongTermMemoryStore,
) : SettingsPort {
    override fun snapshot(): SettingsSnapshot = config.toProtocol(providerCatalog)

    @Transactional
    override fun save(
        settings: SettingsSnapshot,
        providerConfiguration: de.heckenmann.visualagent.protocol.ProviderConfiguration?,
    ) {
        val currentMemory = mainAgentMemoryStore.snapshot()
        val memoryLimit = settings.maxMainAgentMemoryChars.coerceIn(AppConfigBean.MAIN_AGENT_MEMORY_CHARS_RANGE)
        require(memoryLimit >= currentMemory.contentLength) {
            "Main-agent memory has ${currentMemory.contentLength} characters; reduce it before lowering the limit below that size"
        }
        providerConfiguration?.let { configuration ->
            providerCatalog.replaceConfiguration(
                ProviderConfiguration(
                    providers = configuration.providers.map { profile -> profile.toApplication() },
                    providerId = configuration.providerId,
                    modelId = configuration.modelId,
                ),
            )
        }
        config.apply {
            uiThemeMode = settings.uiThemeMode.toApplication()
            fontSize = settings.fontSize
            uiScalePercent = settings.uiScalePercent?.coerceIn(UI_SCALE_PERCENT_RANGE)
            showPanelLabels = settings.showPanelLabels
            contextLength = settings.contextLength
            loadLimit = settings.loadLimit
            maxParallelSubAgents = settings.maxParallelSubAgents
            timeoutSeconds = settings.timeoutSeconds
            userModelInstruction = settings.userModelInstruction
            favoriteModels = settings.favoriteModels.joinToString(",")
            queueFlushMode = settings.queueFlushMode
            maxMainAgentMemoryChars = memoryLimit
            save()
        }
    }

    override fun addChangeListener(listener: (SettingsSnapshot) -> Unit): AutoCloseable = config.addChangeListener { listener(snapshot()) }
}

private fun AppConfigBean.toProtocol(providerCatalog: ProviderCatalogService): SettingsSnapshot =
    SettingsSnapshot(
        providerId = providerCatalog.activeProviderId(),
        modelId = providerCatalog.activeModelId(),
        uiThemeMode = uiThemeMode.toProtocol(),
        fontSize = fontSize,
        uiScalePercent = uiScalePercent,
        showPanelLabels = showPanelLabels,
        contextLength = contextLength,
        loadLimit = loadLimit,
        maxParallelSubAgents = maxParallelSubAgents,
        timeoutSeconds = timeoutSeconds,
        userModelInstruction = userModelInstruction,
        favoriteModels = favoriteModels.split(',').map(String::trim).filter(String::isNotBlank),
        queueFlushMode = queueFlushMode,
        maxMainAgentMemoryChars = maxMainAgentMemoryChars,
    )

private fun ApplicationThemeMode.toProtocol(): ThemeMode = ThemeMode.valueOf(name)

private fun ThemeMode.toApplication(): ApplicationThemeMode = ApplicationThemeMode.valueOf(name)

private fun de.heckenmann.visualagent.protocol.ProviderProfile.toApplication(): de.heckenmann.visualagent.agent.provider.ProviderProfile =
    de.heckenmann.visualagent.agent.provider.ProviderProfile(
        id = id,
        name = name,
        adapter =
            de.heckenmann.visualagent.agent.provider.ProviderAdapter
                .valueOf(adapter.name),
        baseUrl = baseUrl,
        apiKey = apiKey,
        enabled = enabled,
        defaultModel = defaultModel,
        options = options,
        models =
            models.map { model ->
                de.heckenmann.visualagent.agent.provider.ProviderModelConfig(
                    id = model.id,
                    name = model.name,
                    status =
                        de.heckenmann.visualagent.agent.provider.ModelStatus
                            .valueOf(model.status.name),
                    options = model.options,
                    variants = model.variants,
                    contextLimit = model.contextLimit,
                    outputLimit = model.outputLimit,
                    capabilities = model.capabilities,
                )
            },
        modelWhitelist = modelWhitelist,
        modelBlacklist = modelBlacklist,
    )

private val UI_SCALE_PERCENT_RANGE = 50..200
