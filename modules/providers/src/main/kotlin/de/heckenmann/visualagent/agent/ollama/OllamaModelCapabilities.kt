package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.provider.ProviderProfile
import mu.KotlinLogging
import org.springframework.ai.ollama.api.OllamaApi
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

private val logger = KotlinLogging.logger {}

/** Reads each model's capability declaration from Ollama's `/api/show` endpoint. */
internal fun fetchModelCapabilitiesReactive(
    profile: ProviderProfile,
    api: OllamaApi,
): Mono<Map<String, Set<String>>> =
    Mono
        .fromCallable {
            api
                .listModels()
                .models()
                .mapNotNull { it.name() }
                .distinct()
        }.subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux<String>::fromIterable)
        .flatMapSequential({ model ->
            Mono
                .fromCallable {
                    api
                        .showModel(OllamaApi.ShowModelRequest(model))
                        .capabilities()
                        .takeIf(List<String>::isNotEmpty)
                        ?.let { model to it.toSet() }
                }.subscribeOn(Schedulers.boundedElastic())
                .onErrorResume { error ->
                    logger.warn(error) { "Failed to read Ollama capabilities for model=$model provider=${profile.id}" }
                    Mono.empty()
                }
        }, 4)
        .collectMap({ it.first }, { it.second })
        .map { it.toMap() }
        .onErrorResume { error ->
            logger.warn(error) { "Failed to list Ollama models for capability discovery provider=${profile.id}" }
            Mono.just(emptyMap())
        }
