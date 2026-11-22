package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ModelCapability
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.supportsCapability
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** Discovers model catalogs and persists provider-reported model metadata. */
internal fun loadProviderModelIdsReactive(
    catalog: ProviderCatalogService,
    providerId: String,
    ollamaClient: OllamaClient,
    openAiClient: OpenAiClient,
    profiledAdapters: List<ProfiledProviderAdapter>,
    fetchCapabilities: (ProviderProfile) -> Mono<Map<String, Set<String>>>,
): Mono<List<String>> =
    Mono
        .fromCallable { catalog.getProvider(providerId) ?: error("Provider not found: $providerId") }
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap { profile ->
            discoverProviderModelConfigsReactive(profile, ollamaClient, openAiClient, profiledAdapters, fetchCapabilities)
                .flatMap { discovered ->
                    if (profile.adapter == ProviderAdapter.OLLAMA) {
                        catalog.updateDiscoveredModels(providerId, discovered.map(ProviderModelConfig::id))
                        catalog.updateModelCapabilities(
                            providerId,
                            discovered.filter(ProviderModelConfig::capabilitiesComplete).associate { it.id to it.capabilities },
                        )
                    } else {
                        catalog.updateDiscoveredModelConfigs(providerId, discovered)
                    }
                    Mono.just(catalog.selectableModels(providerId).map(ProviderModelConfig::id))
                }
        }

/** Discovers model IDs for a staged profile without persisting its metadata. */
internal fun discoverProviderModelIdsReactive(
    profile: ProviderProfile,
    ollamaClient: OllamaClient,
    openAiClient: OpenAiClient,
    profiledAdapters: List<ProfiledProviderAdapter>,
    fetchCapabilities: (ProviderProfile) -> Mono<Map<String, Set<String>>>,
): Mono<List<String>> =
    discoverProviderModelConfigsReactive(profile, ollamaClient, openAiClient, profiledAdapters, fetchCapabilities)
        .map { configs -> configs.map(ProviderModelConfig::id) }

/** Discovers detailed model metadata for a staged profile without persisting it. */
internal fun discoverProviderModelConfigsReactive(
    profile: ProviderProfile,
    ollamaClient: OllamaClient,
    openAiClient: OpenAiClient,
    profiledAdapters: List<ProfiledProviderAdapter>,
    fetchCapabilities: (ProviderProfile) -> Mono<Map<String, Set<String>>>,
): Mono<List<ProviderModelConfig>> =
    when (profile.adapter) {
        ProviderAdapter.OLLAMA ->
            ollamaClient.getModelsReactive(profile).flatMap { names ->
                fetchCapabilities(profile).map { capabilities ->
                    names.map { name ->
                        val reported = capabilities[name]
                        ProviderModelConfig(id = name, capabilities = reported.orEmpty(), capabilitiesComplete = reported != null)
                    }
                }
            }
        ProviderAdapter.OPENAI_COMPATIBLE ->
            runCatching { openAiClient.getModelConfigsReactive(profile) }
                .getOrElse { openAiClient.getModelsReactive(profile).map { models -> models.map(::ProviderModelConfig) } }
        ProviderAdapter.CODEX_CLI, ProviderAdapter.CODEX_DIRECT ->
            profiledAdapters
                .singleOrNull { it.adapter == profile.adapter }
                ?.loadModelsReactive(profile)
                ?: error("Provider adapter is unavailable: ${profile.adapter}")
    }

/** Applies an explicit user-selected task model to an image-bearing request when eligible. */
internal fun ChatRequestContext.withTaskModelAssignment(catalog: ProviderCatalogService): ChatRequestContext {
    if (metadata["capabilityModelExplicit"] == true) return this
    val capability =
        (metadata["modelCapability"] as? String)?.let { name ->
            runCatching { ModelCapability.valueOf(name) }.getOrNull()
        } ?: ModelCapability.VISION.takeIf { messages.lastOrNull { it.role == "user" }?.images?.isNotEmpty() == true } ?: return this
    if (capability != ModelCapability.VISION) return this
    val selection = catalog.visionTaskModelAssignment(provider) ?: return this
    val resolved = catalog.resolve(selection.first, selection.second)
    require(resolved.model.supportsCapability(capability)) {
        "Model '${selection.first}/${selection.second}' does not support $capability"
    }
    return copy(provider = resolved.provider.id, model = resolved.model.id)
}
