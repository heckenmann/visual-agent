package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.createOllamaApi
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfigBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.ai.ollama.api.OllamaApi

/**
 * Connection and model-discovery operations for [OllamaClient].
 *
 * These do not participate in the chat/stream path and are kept in a separate
 * file so that [OllamaClient] stays under the project line-of-code limit.
 */
internal class OllamaClientOps(
    private val ollamaApi: OllamaApi,
    private val appConfig: AppConfigBean,
) {
    private val logger = KotlinLogging.logger {}

    /** Always reports the client as connected; the actual liveness check is lazy. */
    fun isConnected(): Boolean = true

    /**
     * Pings the configured Ollama endpoint to verify reachability.
     *
     * @return `true` when the remote API responded, `false` on any failure
     */
    suspend fun checkConnection(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                ollamaApi.listModels()
                true
            } catch (e: Exception) {
                logger.warn(e) { "Ollama connection check failed" }
                false
            }
        }

    /**
     * Lists models available on the configured Ollama endpoint.
     *
     * @return Model names, falling back to the configured model when discovery fails
     */
    suspend fun getModels(): List<String> =
        withContext(Dispatchers.IO) {
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
        }

    /**
     * Lists models available on a specific provider profile's endpoint.
     *
     * @param profile Provider profile to query
     * @return Model names, falling back to the profile's default model when discovery fails
     */
    suspend fun getModels(profile: ProviderProfile): List<String> =
        withContext(Dispatchers.IO) {
            val models =
                createOllamaApi(profile, appConfig)
                    .listModels()
                    .models()
                    .mapNotNull { model -> model.name() }
                    .distinct()
            if (models.isEmpty()) listOf(profile.defaultModel).filter(String::isNotBlank) else models
        }

    /**
     * Retrieves metadata about a specific model on a provider profile.
     *
     * @param profile Provider profile to query
     * @param modelName Model name to inspect
     * @return Show response with model details
     */
    suspend fun getModelDetails(
        profile: ProviderProfile,
        modelName: String,
    ): ShowResponse =
        withContext(Dispatchers.IO) {
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
                    ),
            )
        }

    /**
     * Retrieves metadata about a specific model on the configured endpoint.
     *
     * @param modelName Model name to inspect
     * @return Show response, with empty details when the call fails
     */
    suspend fun getModelDetails(modelName: String): ShowResponse =
        withContext(Dispatchers.IO) {
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
                        ),
                )
            } catch (e: Exception) {
                logger.warn(e) { "Ollama model details failed for $modelName" }
                ShowResponse(model = modelName, modifiedAt = "")
            }
        }
}
