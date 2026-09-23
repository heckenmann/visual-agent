package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.createOllamaApi
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderRuntimeConfig
import mu.KotlinLogging
import org.springframework.ai.ollama.api.OllamaApi
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Connection and model-discovery operations for [OllamaClient].
 *
 * These do not participate in the chat/stream path and are kept in a separate
 * file so that [OllamaClient] stays under the project line-of-code limit.
 */
internal class OllamaClientOps(
    private val ollamaApi: OllamaApi,
    private val appConfig: ProviderRuntimeConfig,
) {
    private val logger = KotlinLogging.logger {}

    /** Always reports the client as connected; the actual liveness check is lazy. */
    fun isConnected(): Boolean = true

    /**
     * Pings the configured Ollama endpoint to verify reachability.
     *
     * @return `true` when the remote API responded, `false` on any failure
     */
    fun checkConnectionReactive(): Mono<Boolean> =
        Mono
            .fromCallable {
                try {
                    ollamaApi.listModels()
                    true
                } catch (e: Exception) {
                    logger.warn(e) { "Ollama connection check failed" }
                    false
                }
            }.subscribeOn(Schedulers.boundedElastic())

    /** Pings the Ollama endpoint configured by a specific provider profile. */
    fun checkConnectionReactive(profile: ProviderProfile): Mono<Boolean> =
        Mono
            .fromCallable {
                try {
                    createOllamaApi(profile, appConfig).listModels()
                    true
                } catch (e: Exception) {
                    logger.warn(e) { "Ollama profile connection check failed" }
                    false
                }
            }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Lists models available on the configured Ollama endpoint.
     *
     * @return Model names, falling back to the configured model when discovery fails
     */
    fun getModelsReactive(): Mono<List<String>> =
        Mono
            .fromCallable {
                val configuredModel = appConfig.ollamaModel
                try {
                    val models =
                        ollamaApi
                            .listModels()
                            .models()
                            .mapNotNull { model -> model.name() }
                            .distinct()
                    if (models.isEmpty()) listOf(configuredModel) else models
                } catch (e: Exception) {
                    logger.warn(e) { "Ollama model list failed; falling back to configured model" }
                    listOf(configuredModel)
                }
            }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Lists models available on a specific provider profile's endpoint.
     *
     * @param profile Provider profile to query
     * @return Model names, falling back to the profile's default model when discovery fails
     */
    fun getModelsReactive(profile: ProviderProfile): Mono<List<String>> =
        Mono
            .fromCallable {
                val models =
                    createOllamaApi(profile, appConfig)
                        .listModels()
                        .models()
                        .mapNotNull { model -> model.name() }
                        .distinct()
                if (models.isEmpty()) listOf(profile.defaultModel).filter(String::isNotBlank) else models
            }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Retrieves metadata about a specific model on a provider profile.
     *
     * @param profile Provider profile to query
     * @param modelName Model name to inspect
     * @return Show response with model details
     */
    fun getModelDetailsReactive(
        profile: ProviderProfile,
        modelName: String,
    ): Mono<ShowResponse> =
        Mono
            .fromCallable {
                val response = createOllamaApi(profile, appConfig).showModel(OllamaApi.ShowModelRequest(modelName))
                ShowResponse(
                    model = modelName,
                    modifiedAt = "",
                    details =
                        ModelDetails(
                            family = response.details().family(),
                            format = response.details().format(),
                            parameterSize = response.details().parameterSize(),
                            quantizationLevel = response.details().quantizationLevel(),
                            contextLimit = contextLimit(response.modelInfo()),
                        ),
                )
            }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Retrieves metadata about a specific model on the configured endpoint.
     *
     * @param modelName Model name to inspect
     * @return Show response, with empty details when the call fails
     */
    fun getModelDetailsReactive(modelName: String): Mono<ShowResponse> =
        Mono
            .fromCallable {
                try {
                    val response = ollamaApi.showModel(OllamaApi.ShowModelRequest(modelName))
                    val details = response.details()
                    ShowResponse(
                        model = modelName,
                        modifiedAt = response.modifiedAt().toString(),
                        parameters = response.parameters(),
                        template = response.template(),
                        system = response.system(),
                        license = response.license(),
                        details =
                            ModelDetails(
                                parentModel = details.parentModel(),
                                format = details.format(),
                                family = details.family(),
                                families = details.families(),
                                parameterSize = details.parameterSize(),
                                quantizationLevel = details.quantizationLevel(),
                                contextLimit = contextLimit(response.modelInfo()),
                            ),
                    )
                } catch (e: Exception) {
                    logger.warn(e) { "Ollama model details failed for $modelName" }
                    ShowResponse(model = modelName, modifiedAt = "")
                }
            }.subscribeOn(Schedulers.boundedElastic())
}

private fun contextLimit(modelInfo: Map<String, Any>): Int? =
    modelInfo.entries
        .firstOrNull { (key, value) ->
            key.endsWith("context_length", ignoreCase = true) && value.toString().toIntOrNull() != null
        }?.value
        ?.toString()
        ?.toIntOrNull()
