package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ModelCapability
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.supportsCapability
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** Resolves and executes embeddings with the exact configured provider and model. */
internal fun routeProviderEmbeddings(
    text: String,
    selection: ModelSelection,
    providerCatalog: ProviderCatalogService,
    ollamaClient: OllamaClient,
    openAiClient: OpenAiClient,
): Mono<List<Double>> =
    Mono
        .fromCallable {
            val (provider, model) = selection
            val resolved = providerCatalog.resolve(provider, model)
            require(resolved.model.supportsCapability(ModelCapability.EMBEDDING)) {
                "Model '${resolved.provider.id}/${resolved.model.id}' does not support EMBEDDING"
            }
            resolved
        }.subscribeOn(Schedulers.boundedElastic())
        .flatMap { resolved ->
            val profile = resolved.provider
            val modelId = resolved.model.id
            when (profile.adapter) {
                ProviderAdapter.OLLAMA -> ollamaClient.embeddingsReactive(text, modelId, profile)
                ProviderAdapter.OPENAI_COMPATIBLE -> openAiClient.embeddingsReactive(text, modelId, profile)
                ProviderAdapter.CODEX_CLI, ProviderAdapter.CODEX_DIRECT ->
                    Mono.error(UnsupportedOperationException("Provider '${profile.id}' cannot execute EMBEDDING tasks"))
            }
        }
