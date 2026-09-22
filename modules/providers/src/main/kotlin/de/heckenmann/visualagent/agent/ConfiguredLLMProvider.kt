package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.fetchModelCapabilitiesReactive
import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.DefaultProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderEnvironmentCredentials
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderRuntimeConfig
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Primary provider facade that delegates model operations to the configured backend.
 */
@Primary
@Component
class ConfiguredLLMProvider(
    private val ollamaClient: OllamaClient,
    private val openAiClient: OpenAiClient,
    private val providerCatalog: ProviderCatalogService,
    private val fetchCapabilities: (ProviderProfile) -> Mono<Map<String, Set<String>>> = ::fetchModelCapabilitiesReactive,
    private val profiledAdapters: List<ProfiledProviderAdapter> = emptyList(),
    private val runtimeConfig: ProviderRuntimeConfig = DefaultProviderRuntimeConfig(),
) : LLMProvider {
    override fun chatReactive(messages: List<Message>): Mono<ChatResponse> = chatReactive(ChatRequestContext(messages = messages))

    override fun chatReactive(request: ChatRequestContext): Mono<ChatResponse> =
        resolveRequest(request).flatMap { resolved -> providerFor(resolved.providerProfile).chatReactive(resolved) }

    override fun streamReactive(messages: List<Message>): Flux<ChatResponse> = streamReactive(ChatRequestContext(messages = messages))

    override fun streamReactive(request: ChatRequestContext): Flux<ChatResponse> =
        resolveRequest(request).flatMapMany { resolved -> providerFor(resolved.providerProfile).streamReactive(resolved) }

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
    ): Mono<ChatResponse> =
        Mono
            .fromCallable {
                val providerId = providerCatalog.activeProviderId()
                val profile =
                    providerCatalog.getProvider(providerId)
                        ?: error("Active provider profile is missing: $providerId")
                val modelId = providerCatalog.activeModelId().ifBlank { profile.defaultModel }
                requireVisionCapability(profile, modelId)
                profile to modelId
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { (profile, modelId) ->
                if (profile.adapter == ProviderAdapter.CODEX_CLI) {
                    adapterFor(profile.adapter).visionReactive(image, prompt, modelId, profile)
                } else {
                    providerFor(profile).visionReactive(image, prompt, modelId)
                }
            }

    override fun embeddingsReactive(text: String): Mono<List<Double>> =
        activeProviderSelectionReactive().flatMap { (provider, modelId) -> provider.embeddingsReactive(text, modelId) }

    override fun isConnected(): Boolean {
        val profile = providerCatalog.getProvider(providerCatalog.activeProviderId()) ?: return false
        return when (profile.adapter) {
            ProviderAdapter.OLLAMA -> true
            ProviderAdapter.OPENAI_COMPATIBLE -> ProviderEnvironmentCredentials.openAiApiKey(profile).isNotBlank()
            ProviderAdapter.CODEX_CLI -> adapterFor(profile.adapter).isConnected()
        }
    }

    override fun checkConnectionReactive(): Mono<Boolean> =
        Mono
            .fromCallable {
                providerCatalog.getProvider(providerCatalog.activeProviderId())
                    ?: error("Active provider profile is missing")
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { profile -> checkConnectionReactive(profile) }
            .onErrorReturn(false)

    override fun getModelsReactive(): Mono<List<String>> = getModelsReactive(providerCatalog.activeProviderId())

    override fun getModelsReactive(providerId: String): Mono<List<String>> =
        Mono
            .fromCallable { providerCatalog.getProvider(providerId) ?: error("Provider not found: $providerId") }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { profile ->
                discoverModelConfigsReactive(profile).flatMap { discovered ->
                    if (profile.adapter == ProviderAdapter.CODEX_CLI) {
                        providerCatalog.updateDiscoveredModelConfigs(providerId, discovered)
                    } else {
                        providerCatalog.updateDiscoveredModels(providerId, discovered.map { it.id })
                    }
                    if (profile.adapter == ProviderAdapter.OLLAMA) {
                        fetchCapabilities(profile)
                            .doOnNext { capabilities ->
                                providerCatalog.updateModelCapabilities(providerId, capabilities)
                            }.then()
                    } else {
                        Mono.empty()
                    }.thenReturn(providerCatalog.selectableModels(providerId).map { it.id })
                }
            }

    override fun getModelsReactive(profile: ProviderProfile): Mono<List<String>> =
        discoverModelConfigsReactive(profile).map { configs -> configs.map(ProviderModelConfig::id) }

    override fun getModelConfigsReactive(profile: ProviderProfile): Mono<List<ProviderModelConfig>> = discoverModelConfigsReactive(profile)

    override fun getModelDetailsReactive(modelName: String): Mono<ShowResponse> =
        getModelDetailsReactive(providerCatalog.activeProviderId(), modelName)

    override fun getModelDetailsReactive(
        providerId: String,
        modelName: String,
    ): Mono<ShowResponse> =
        Mono
            .fromCallable { providerCatalog.getProvider(providerId) ?: error("Provider not found: $providerId") }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { profile ->
                when (profile.adapter) {
                    ProviderAdapter.OLLAMA -> ollamaClient.getModelDetailsReactive(profile, modelName)
                    ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient.getModelDetailsReactive(profile, modelName)
                    ProviderAdapter.CODEX_CLI -> adapterFor(profile.adapter).getModelDetailsReactive(profile, modelName)
                }
            }

    private fun discoverModelConfigsReactive(profile: ProviderProfile): Mono<List<ProviderModelConfig>> =
        when (profile.adapter) {
            ProviderAdapter.OLLAMA -> ollamaClient.getModelsReactive(profile).map { models -> models.map(::ProviderModelConfig) }
            ProviderAdapter.OPENAI_COMPATIBLE ->
                openAiClient.getModelsReactive(profile).map { models -> models.map(::ProviderModelConfig) }
            ProviderAdapter.CODEX_CLI -> adapterFor(profile.adapter).loadModelsReactive(profile)
        }

    private fun checkConnectionReactive(profile: ProviderProfile): Mono<Boolean> =
        when (profile.adapter) {
            ProviderAdapter.OLLAMA -> ollamaClient.checkConnectionReactive(profile)
            ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient.checkConnectionReactive(profile)
            ProviderAdapter.CODEX_CLI -> adapterFor(profile.adapter).checkConnectionReactive(profile)
        }

    private fun activeProviderSelectionReactive(): Mono<Pair<LLMProvider, String>> =
        Mono
            .fromCallable {
                val providerId = providerCatalog.activeProviderId()
                val profile =
                    providerCatalog.getProvider(providerId)
                        ?: error("Active provider profile is missing: $providerId")
                val modelId = providerCatalog.activeModelId().ifBlank { profile.defaultModel }
                providerFor(profile) to modelId
            }.subscribeOn(Schedulers.boundedElastic())

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

    private fun resolveRequest(request: ChatRequestContext): Mono<ChatRequestContext> =
        Mono
            .fromCallable { request.withConfiguredContextLimit().resolve() }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(::refreshModelContextLimit)

    private fun refreshModelContextLimit(request: ChatRequestContext): Mono<ChatRequestContext> {
        val profile = request.providerProfile ?: return Mono.just(request)
        val model = request.model ?: return Mono.just(request)
        val modelDetails =
            runCatching { modelDetailsReactive(profile, model) }
                .getOrElse { return Mono.just(request) }
        return modelDetails
            .map { details ->
                request.copy(
                    contextWindow =
                        request.contextWindow.copy(
                            modelLimit = details.details?.contextLimit ?: request.contextWindow.modelLimit,
                        ),
                )
            }.onErrorReturn(request)
    }

    private fun ChatRequestContext.withConfiguredContextLimit(): ChatRequestContext =
        copy(
            contextWindow =
                contextWindow.copy(
                    configuredLimit = contextWindow.configuredLimit ?: runtimeConfig.contextLength,
                ),
        )

    private fun modelDetailsReactive(
        profile: ProviderProfile,
        model: String,
    ): Mono<ShowResponse> =
        when (profile.adapter) {
            ProviderAdapter.OLLAMA -> ollamaClient.getModelDetailsReactive(profile, model)
            ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient.getModelDetailsReactive(profile, model)
            ProviderAdapter.CODEX_CLI -> adapterFor(profile.adapter).getModelDetailsReactive(profile, model)
        }

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
        providerProfile?.let { stagedProfile ->
            val stagedModel = model?.takeIf(String::isNotBlank) ?: stagedProfile.defaultModel
            require(stagedModel.isNotBlank()) { "A staged provider request requires a model." }
            return copy(
                provider = stagedProfile.id,
                model = stagedModel,
                providerProfile = stagedProfile,
                contextWindow = stagedProfile.contextWindow(stagedModel, contextWindow),
                modelCapabilities =
                    stagedProfile.models
                        .firstOrNull { it.id == stagedModel }
                        ?.capabilities
                        .orEmpty(),
                modelCapabilitiesComplete = stagedProfile.models.firstOrNull { it.id == stagedModel }?.capabilitiesComplete == true,
            )
        }
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
            modelCapabilitiesComplete = resolved.model.capabilitiesComplete,
            contextWindow =
                contextWindow.copy(
                    modelLimit = resolved.model.contextLimit,
                    outputLimit = resolved.model.outputLimit,
                ),
        )
    }

    private fun ProviderProfile.contextWindow(
        modelId: String,
        current: ContextWindow,
    ): ContextWindow {
        val modelConfig = models.firstOrNull { it.id == modelId }
        return current.copy(
            modelLimit = modelConfig?.contextLimit,
            outputLimit = modelConfig?.outputLimit,
        )
    }
}
