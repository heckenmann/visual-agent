package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.DefaultProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.ModelCapability
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderEnvironmentCredentials
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.capabilitySupport
import de.heckenmann.visualagent.agent.provider.supportsCapability
import de.heckenmann.visualagent.model.CapabilitySupport
import org.springframework.ai.content.Media
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
    private val fetchCapabilities: (ProviderProfile) -> Mono<Map<String, Set<String>>> = ollamaClient::getModelCapabilitiesReactive,
    private val profiledAdapters: List<ProfiledProviderAdapter> = emptyList(),
    private val runtimeConfig: ProviderRuntimeConfig = DefaultProviderRuntimeConfig(),
) : LLMProvider {
    override fun fileReactive(
        file: Media,
        request: ChatRequestContext,
    ): Mono<ChatResponse> =
        resolveRequest(request).flatMap { resolved -> providerFor(resolved.providerProfile).fileReactive(file, resolved) }

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
                val selected = providerCatalog.visionTaskModelAssignment()
                if (selected != null) {
                    val resolved = providerCatalog.resolve(selected.first, selected.second)
                    require(resolved.model.supportsCapability(ModelCapability.VISION)) {
                        "Model '${selected.first}/${selected.second}' does not support VISION"
                    }
                    return@fromCallable resolved.provider to resolved.model.id
                }
                val providerId = providerCatalog.activeProviderId()
                val profile =
                    providerCatalog.getProvider(providerId)
                        ?: error("Active provider profile is missing: $providerId")
                val modelId = providerCatalog.activeModelId().ifBlank { profile.defaultModel }
                requireVisionCapability(profile, modelId)
                profile to modelId
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { (profile, modelId) ->
                when (profile.adapter) {
                    ProviderAdapter.CODEX_CLI, ProviderAdapter.CODEX_DIRECT ->
                        adapterFor(
                            profile.adapter,
                        ).visionReactive(image, prompt, modelId, profile)
                    ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient.visionReactive(image, prompt, modelId, profile)
                    ProviderAdapter.OLLAMA -> ollamaClient.visionReactive(image, prompt, modelId, profile)
                }
            }

    /** Executes a vision request with the selected agent's provider and model. */
    override fun visionReactive(
        image: ByteArray,
        prompt: String,
        selection: ModelSelection,
    ): Mono<ChatResponse> = routeVision(image, prompt, selection, useTaskDefault = false)

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
        selection: ModelSelection,
        useTaskDefault: Boolean,
    ): Mono<ChatResponse> = routeVision(image, prompt, selection, useTaskDefault)

    private fun routeVision(
        image: ByteArray,
        prompt: String,
        selection: ModelSelection,
        useTaskDefault: Boolean,
    ): Mono<ChatResponse> =
        Mono
            .fromCallable {
                val taskSelection = if (useTaskDefault) providerCatalog.visionTaskModelAssignment(selection.provider) else null
                val resolved = providerCatalog.resolve(taskSelection?.first ?: selection.provider, taskSelection?.second ?: selection.model)
                require(resolved.model.supportsCapability(ModelCapability.VISION)) {
                    "Model '${resolved.provider.id}/${resolved.model.id}' does not support VISION. " +
                        "Pick a vision-capable model in the sub-agent settings."
                }
                resolved
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { resolved ->
                when (resolved.provider.adapter) {
                    ProviderAdapter.CODEX_CLI, ProviderAdapter.CODEX_DIRECT ->
                        adapterFor(resolved.provider.adapter)
                            .visionReactive(image, prompt, resolved.model.id, resolved.provider)
                    ProviderAdapter.OPENAI_COMPATIBLE ->
                        openAiClient.visionReactive(image, prompt, resolved.model.id, resolved.provider)
                    ProviderAdapter.OLLAMA ->
                        ollamaClient.visionReactive(image, prompt, resolved.model.id, resolved.provider)
                }
            }

    override fun embeddingsReactive(text: String): Mono<List<Double>> =
        Mono
            .fromCallable {
                val selected = providerCatalog.taskModelAssignment(ModelCapability.EMBEDDING)
                if (selected == null) return@fromCallable null
                val resolved = providerCatalog.resolve(selected.first, selected.second)
                require(resolved.model.supportsCapability(ModelCapability.EMBEDDING)) {
                    "Model '${selected.first}/${selected.second}' does not support EMBEDDING"
                }
                require(resolved.provider.adapter == ProviderAdapter.OLLAMA) {
                    "Provider '${resolved.provider.id}' cannot execute EMBEDDING tasks"
                }
                resolved
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { resolved -> ollamaClient.embeddingsReactive(text, resolved.model.id, resolved.provider) }
            .switchIfEmpty(
                activeProviderSelectionReactive().flatMap { (provider, model) -> provider.embeddingsReactive(text, model) },
            )

    override fun isConnected(): Boolean {
        val profile = providerCatalog.getProvider(providerCatalog.activeProviderId()) ?: return false
        return when (profile.adapter) {
            ProviderAdapter.OLLAMA -> true
            ProviderAdapter.OPENAI_COMPATIBLE -> ProviderEnvironmentCredentials.openAiApiKey(profile).isNotBlank()
            ProviderAdapter.CODEX_CLI, ProviderAdapter.CODEX_DIRECT -> adapterFor(profile.adapter).isConnected()
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
        loadProviderModelIdsReactive(providerCatalog, providerId, ollamaClient, openAiClient, profiledAdapters, fetchCapabilities)

    override fun getModelsReactive(profile: ProviderProfile): Mono<List<String>> =
        discoverProviderModelIdsReactive(profile, ollamaClient, openAiClient, profiledAdapters, fetchCapabilities)

    override fun getModelConfigsReactive(profile: ProviderProfile): Mono<List<ProviderModelConfig>> =
        discoverProviderModelConfigsReactive(profile, ollamaClient, openAiClient, profiledAdapters, fetchCapabilities)

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
                    ProviderAdapter.CODEX_CLI, ProviderAdapter.CODEX_DIRECT ->
                        adapterFor(
                            profile.adapter,
                        ).getModelDetailsReactive(profile, modelName)
                }.map { it.withCatalogCapabilities(profile, modelName) }
            }

    private fun checkConnectionReactive(profile: ProviderProfile): Mono<Boolean> =
        when (profile.adapter) {
            ProviderAdapter.OLLAMA -> ollamaClient.checkConnectionReactive(profile)
            ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient.checkConnectionReactive(profile)
            ProviderAdapter.CODEX_CLI, ProviderAdapter.CODEX_DIRECT -> adapterFor(profile.adapter).checkConnectionReactive(profile)
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
            ProviderAdapter.CODEX_CLI, ProviderAdapter.CODEX_DIRECT -> adapterFor(profile.adapter)
            null -> error("Resolved provider profile is missing")
        }

    private fun adapterFor(adapter: ProviderAdapter): ProfiledProviderAdapter =
        profiledAdapters.singleOrNull { it.adapter == adapter }
            ?: error("Provider adapter is unavailable: $adapter")

    private fun resolveRequest(request: ChatRequestContext): Mono<ChatRequestContext> =
        Mono
            .fromCallable { request.withConfiguredContextLimit().withTaskModelAssignment(providerCatalog).resolve() }
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
            ProviderAdapter.CODEX_CLI, ProviderAdapter.CODEX_DIRECT -> adapterFor(profile.adapter).getModelDetailsReactive(profile, model)
        }

    private fun requireVisionCapability(
        profile: ProviderProfile,
        modelId: String,
    ) {
        val model = profile.models.firstOrNull { it.id == modelId } ?: return
        if (model.capabilitySupport(ModelCapability.VISION) == CapabilitySupport.UNSUPPORTED) {
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
                modelCapabilityOverrides =
                    stagedProfile.models
                        .firstOrNull { it.id == stagedModel }
                        ?.capabilityOverrides
                        .orEmpty(),
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
            modelCapabilityOverrides = resolved.model.capabilityOverrides,
            contextWindow =
                contextWindow.copy(
                    modelLimit = resolved.model.contextLimit,
                    outputLimit = resolved.model.outputLimit,
                ),
        )
    }
}
