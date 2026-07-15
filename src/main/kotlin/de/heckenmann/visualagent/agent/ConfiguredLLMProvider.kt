package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.fetchModelCapabilities
import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
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
) : LLMProvider {
    override suspend fun chat(messages: List<Message>): ChatResponse = activeProvider().chat(messages)

    override suspend fun chat(request: ChatRequestContext): ChatResponse {
        val resolved = request.resolve()
        return providerFor(resolved.providerProfile).chat(resolved)
    }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = activeProvider().stream(messages)

    override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> {
        val resolved = request.resolve()
        return providerFor(resolved.providerProfile).stream(resolved)
    }

    override suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse = activeProvider().vision(image, prompt)

    override suspend fun embeddings(text: String): List<Double> = activeProvider().embeddings(text)

    override fun isConnected(): Boolean {
        val profile = providerCatalog.getProvider(providerCatalog.activeProviderId()) ?: return false
        return profile.adapter == ProviderAdapter.OLLAMA || profile.apiKey.isNotBlank()
    }

    override suspend fun checkConnection(): Boolean =
        runCatching { getModels(providerCatalog.activeProviderId()).isNotEmpty() }.getOrDefault(false)

    override suspend fun getModels(): List<String> = getModels(providerCatalog.activeProviderId())

    override suspend fun getModels(providerId: String): List<String> {
        val profile = providerCatalog.getProvider(providerId) ?: error("Provider not found: $providerId")
        val discovered =
            when (profile.adapter) {
                ProviderAdapter.OLLAMA -> ollamaClient.getModels(profile)
                ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient.getModels(profile)
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
        }
    }

    private fun activeProvider(): LLMProvider =
        providerCatalog
            .getProvider(providerCatalog.activeProviderId())
            .let(::providerFor)

    private fun providerFor(profile: de.heckenmann.visualagent.agent.provider.ProviderProfile?): LLMProvider =
        when (profile?.adapter) {
            ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient
            else -> ollamaClient
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
