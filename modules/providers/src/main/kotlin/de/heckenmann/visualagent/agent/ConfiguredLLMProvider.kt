package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.fetchModelCapabilities
import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderEnvironmentCredentials
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import kotlinx.coroutines.flow.Flow
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Primary provider facade that delegates model operations to the configured backend.
 */
@Primary
@Component
class ConfiguredLLMProvider(
    private val ollamaClient: OllamaClient,
    private val openAiClient: OpenAiClient,
    private val providerCatalog: ProviderCatalogService,
    private val fetchCapabilities: suspend (ProviderProfile) -> Map<String, Set<String>> = ::fetchModelCapabilities,
    private val profiledAdapters: List<ProfiledProviderAdapter> = emptyList(),
) : LLMProvider {
    override suspend fun chat(messages: List<Message>): ChatResponse = chat(ChatRequestContext(messages = messages))

    override suspend fun chat(request: ChatRequestContext): ChatResponse {
        val resolved = request.resolve()
        return providerFor(resolved.providerProfile).chat(resolved)
    }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = stream(ChatRequestContext(messages = messages))

    override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> {
        val resolved = request.resolve()
        return providerFor(resolved.providerProfile).stream(resolved)
    }

    override suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse {
        val providerId = providerCatalog.activeProviderId()
        val profile =
            providerCatalog.getProvider(providerId)
                ?: error("Active provider profile is missing: $providerId")
        val modelId = providerCatalog.activeModelId().ifBlank { profile.defaultModel }
        requireVisionCapability(profile, modelId)
        return if (profile.adapter == ProviderAdapter.CODEX_CLI) {
            adapterFor(profile.adapter).vision(image, prompt, modelId, profile)
        } else {
            providerFor(profile).vision(image, prompt, modelId)
        }
    }

    override suspend fun embeddings(text: String): List<Double> {
        val (provider, modelId) = activeProviderSelection()
        return provider.embeddings(text, modelId)
    }

    override fun isConnected(): Boolean {
        val profile = providerCatalog.getProvider(providerCatalog.activeProviderId()) ?: return false
        return when (profile.adapter) {
            ProviderAdapter.OLLAMA -> true
            ProviderAdapter.OPENAI_COMPATIBLE -> ProviderEnvironmentCredentials.openAiApiKey(profile).isNotBlank()
            ProviderAdapter.CODEX_CLI -> adapterFor(profile.adapter).isConnected()
        }
    }

    override suspend fun checkConnection(): Boolean =
        runCatching { getModels(providerCatalog.activeProviderId()).isNotEmpty() }.getOrDefault(false)

    override suspend fun getModels(): List<String> = getModels(providerCatalog.activeProviderId())

    override suspend fun getModels(providerId: String): List<String> {
        val profile = providerCatalog.getProvider(providerId) ?: error("Provider not found: $providerId")
        if (profile.adapter == ProviderAdapter.CODEX_CLI) {
            val models = adapterFor(profile.adapter).loadModels(profile)
            providerCatalog.updateDiscoveredModelConfigs(providerId, models)
            return providerCatalog.selectableModels(providerId).map { it.id }
        }
        val discovered =
            when (profile.adapter) {
                ProviderAdapter.OLLAMA -> ollamaClient.getModels(profile)
                ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient.getModels(profile)
                ProviderAdapter.CODEX_CLI -> error("Profiled provider models are handled above")
            }
        providerCatalog.updateDiscoveredModels(providerId, discovered)
        if (profile.adapter == ProviderAdapter.OLLAMA) {
            val capabilities = fetchCapabilities(profile)
            providerCatalog.updateModelCapabilities(providerId, capabilities)
        }
        return providerCatalog.selectableModels(providerId).map { it.id }
    }

    override suspend fun getModelDetails(modelName: String): ShowResponse = getModelDetails(providerCatalog.activeProviderId(), modelName)

    override suspend fun getModelDetails(
        providerId: String,
        modelName: String,
    ): ShowResponse {
        val profile = providerCatalog.getProvider(providerId) ?: error("Provider not found: $providerId")
        return when (profile.adapter) {
            ProviderAdapter.OLLAMA -> ollamaClient.getModelDetails(profile, modelName)
            ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient.getModelDetails(profile, modelName)
            ProviderAdapter.CODEX_CLI -> adapterFor(profile.adapter).getModelDetails(profile, modelName)
        }
    }

    private fun activeProviderSelection(): Pair<LLMProvider, String> {
        val providerId = providerCatalog.activeProviderId()
        val profile =
            providerCatalog.getProvider(providerId)
                ?: error("Active provider profile is missing: $providerId")
        val modelId = providerCatalog.activeModelId().ifBlank { profile.defaultModel }
        return providerFor(profile) to modelId
    }

    private fun providerFor(profile: de.heckenmann.visualagent.agent.provider.ProviderProfile?): LLMProvider =
        when (profile?.adapter) {
            ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient
            ProviderAdapter.OLLAMA -> ollamaClient
            ProviderAdapter.CODEX_CLI -> adapterFor(profile.adapter)
            null -> error("Resolved provider profile is missing")
        }

    private fun adapterFor(adapter: ProviderAdapter): ProfiledProviderAdapter =
        profiledAdapters.singleOrNull { it.adapter == adapter }
            ?: error("Provider adapter is unavailable: $adapter")

    private fun requireVisionCapability(
        profile: ProviderProfile,
        modelId: String,
    ) {
        val model = profile.models.firstOrNull { it.id == modelId } ?: return
        if (model.capabilities.isNotEmpty() && model.capabilities.none { it.equals("vision", ignoreCase = true) }) {
            error("Model ${profile.name}/$modelId does not support image input")
        }
    }

    private fun ChatRequestContext.resolve(): ChatRequestContext {
        val explicitOptions =
            buildMap {
                putAll(options)
                parameters.temperature?.let { put("temperature", it.toString()) }
                parameters.topP?.let { put("topP", it.toString()) }
                parameters.maxTokens?.let { put("maxTokens", it.toString()) }
            }
        val resolved = providerCatalog.resolve(provider, model, variant, explicitOptions)
        return copy(
            provider = resolved.provider.id,
            model = resolved.model.id,
            variant = resolved.variant,
            parameters =
                ModelParameters(
                    temperature = resolved.options["temperature"]?.toDoubleOrNull(),
                    topP = (resolved.options["topP"] ?: resolved.options["top_p"])?.toDoubleOrNull(),
                    maxTokens = (resolved.options["maxTokens"] ?: resolved.options["max_tokens"])?.toIntOrNull(),
                ),
            options = resolved.options,
            providerProfile = resolved.provider,
            modelCapabilities = resolved.model.capabilities,
        )
    }
}
