package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.protocol.ModelDetails
import de.heckenmann.visualagent.protocol.ModelStatus
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import org.springframework.stereotype.Component
import de.heckenmann.visualagent.agent.provider.ModelStatus as ApplicationModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter as ApplicationProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile as ApplicationProviderProfile

/** Maps provider catalog and model discovery to the neutral [ProviderPort]. */
@Component
class SpringProviderPort(
    private val providerCatalog: ProviderCatalogService,
    private val llmProvider: LLMProvider,
) : ProviderPort {
    override fun listProviders(): List<ProviderProfile> =
        protocolBoundary {
            providerCatalog.listProviders().map(ApplicationProviderProfile::toProtocol)
        }

    override fun enabledProviders(): List<ProviderProfile> =
        protocolBoundary {
            providerCatalog.enabledProviders().map(ApplicationProviderProfile::toProtocol)
        }

    override fun getProvider(id: String): ProviderProfile? = protocolBoundary { providerCatalog.getProvider(id)?.toProtocol() }

    override fun selectableModels(providerId: String): List<ProviderModel> =
        protocolBoundary { providerCatalog.selectableModels(providerId).map(ProviderModelConfig::toProtocol) }

    override fun activeProviderId(): String = protocolBoundary { providerCatalog.activeProviderId() }

    override fun activeModelId(): String = protocolBoundary { providerCatalog.activeModelId() }

    override fun setActiveProvider(providerId: String) = protocolBoundary { providerCatalog.setActiveProvider(providerId) }

    override fun setActiveSelection(
        providerId: String,
        modelId: String,
    ) = protocolBoundary { providerCatalog.setActiveSelection(providerId, modelId) }

    override fun saveProvider(profile: ProviderProfile) = protocolBoundary { providerCatalog.saveProvider(profile.toApplication()) }

    override fun deleteProvider(providerId: String): Boolean = protocolBoundary { providerCatalog.deleteProvider(providerId) }

    override suspend fun refreshModels(providerId: String): List<ProviderModel> =
        protocolBoundary {
            val discovered = llmProvider.getModels(providerId)
            providerCatalog.updateDiscoveredModels(providerId, discovered)
            selectableModels(providerId)
        }

    override suspend fun modelDetails(
        providerId: String,
        modelId: String,
    ): ModelDetails = protocolBoundary { llmProvider.getModelDetails(providerId, modelId).toProtocol() }

    override fun addChangeListener(listener: () -> Unit): AutoCloseable = providerCatalog.addChangeListener(listener)
}

private fun ApplicationProviderProfile.toProtocol(): ProviderProfile =
    ProviderProfile(
        id = id,
        name = name,
        adapter = adapter.toProtocol(),
        baseUrl = baseUrl,
        apiKey = apiKey,
        enabled = enabled,
        defaultModel = defaultModel,
        options = options,
        models = models.map(ProviderModelConfig::toProtocol),
        modelWhitelist = modelWhitelist,
        modelBlacklist = modelBlacklist,
    )

private fun ProviderProfile.toApplication(): ApplicationProviderProfile =
    ApplicationProviderProfile(
        id = id,
        name = name,
        adapter = adapter.toApplication(),
        baseUrl = baseUrl,
        apiKey = apiKey,
        enabled = enabled,
        defaultModel = defaultModel,
        options = options,
        models = models.map(ProviderModel::toApplication),
        modelWhitelist = modelWhitelist,
        modelBlacklist = modelBlacklist,
    )

private fun ApplicationProviderAdapter.toProtocol(): ProviderAdapter =
    when (this) {
        ApplicationProviderAdapter.OLLAMA -> ProviderAdapter.OLLAMA
        ApplicationProviderAdapter.OPENAI_COMPATIBLE -> ProviderAdapter.OPENAI_COMPATIBLE
        ApplicationProviderAdapter.CODEX_CLI -> ProviderAdapter.CODEX_CLI
    }

private fun ProviderAdapter.toApplication(): ApplicationProviderAdapter =
    when (this) {
        ProviderAdapter.OLLAMA -> ApplicationProviderAdapter.OLLAMA
        ProviderAdapter.OPENAI_COMPATIBLE -> ApplicationProviderAdapter.OPENAI_COMPATIBLE
        ProviderAdapter.CODEX_CLI -> ApplicationProviderAdapter.CODEX_CLI
    }

private fun ProviderModelConfig.toProtocol(): ProviderModel =
    ProviderModel(
        id = id,
        name = name,
        status = status.toProtocol(),
        options = options,
        variants = variants,
        contextLimit = contextLimit,
        outputLimit = outputLimit,
        capabilities = capabilities,
    )

private fun ProviderModel.toApplication(): ProviderModelConfig =
    ProviderModelConfig(
        id = id,
        name = name,
        status = status.toApplication(),
        options = options,
        variants = variants,
        contextLimit = contextLimit,
        outputLimit = outputLimit,
        capabilities = capabilities,
    )

private fun ApplicationModelStatus.toProtocol(): ModelStatus = ModelStatus.valueOf(name)

private fun ModelStatus.toApplication(): ApplicationModelStatus = ApplicationModelStatus.valueOf(name)

private fun ShowResponse.toProtocol(): ModelDetails =
    ModelDetails(
        model = model,
        modifiedAt = modifiedAt,
        family = details?.family,
        parameterSize = details?.parameterSize,
        format = details?.format,
        quantizationLevel = details?.quantizationLevel,
    )
