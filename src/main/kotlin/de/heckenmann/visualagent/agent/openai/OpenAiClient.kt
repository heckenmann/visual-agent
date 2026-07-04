package de.heckenmann.visualagent.agent.openai

import com.openai.client.OpenAIClient
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.ToolCallingLoop
import de.heckenmann.visualagent.agent.VisionSupport
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfig
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.setup.OpenAiSetup
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.net.URI
import java.time.Duration

/**
 * LLM provider implementation for OpenAI and OpenAI-compatible chat endpoints.
 */
@Component
class OpenAiClient(
    private val promptFactory: OpenAiPromptFactory,
    private val toolRegistry: ToolRegistry,
) : LLMProvider {
    override suspend fun chat(messages: List<Message>): ChatResponse = chat(ChatRequestContext(messages = messages))

    override suspend fun chat(request: ChatRequestContext): ChatResponse =
        withContext(Dispatchers.IO) {
            val selectedModel = request.model ?: AppConfig.instance.openAiModel
            val prompt = promptFactory.buildPrompt(request, selectedModel)
            val model = chatModel(request.providerProfile)
            val responseResult =
                runCatching {
                    ToolCallingLoop()
                        .run(
                            model,
                            prompt,
                            toolRegistry.functionCallbacks(
                                request.enabledTools,
                                request.metadata + mapOf("model" to selectedModel, "provider" to "openai"),
                            ),
                        )
                }
            if (responseResult.isFailure) throw buildDetailedProviderError(responseResult.exceptionOrNull())
            responseResult.getOrThrow()
        }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = stream(ChatRequestContext(messages = messages))

    override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> {
        val selectedModel = request.model ?: AppConfig.instance.openAiModel
        val prompt = promptFactory.buildPrompt(request, selectedModel)
        val model = chatModel(request.providerProfile)
        val toolCallbacks =
            if (request.enabledTools.isEmpty()) {
                emptyList()
            } else {
                toolRegistry.functionCallbacks(
                    request.enabledTools,
                    request.metadata + mapOf("model" to selectedModel, "provider" to "openai"),
                )
            }
        return flow {
            try {
                if (toolCallbacks.isEmpty()) {
                    model
                        .stream(prompt)
                        .asFlow()
                        .map { chunk ->
                            ChatResponse(
                                model = chunk.metadata.model.takeIf { it.isNotBlank() } ?: selectedModel,
                                message =
                                    Message(
                                        role = "assistant",
                                        content = chunk.result?.let { it.output.text.orEmpty() }.orEmpty(),
                                    ),
                                done = chunk.result?.metadata?.finishReason != null,
                                promptEvalCount = chunk.metadata.usage.promptTokens,
                                evalCount = chunk.metadata.usage.completionTokens,
                            )
                        }.collect { emit(it) }
                } else {
                    ToolCallingLoop()
                        .runStream(model, prompt, toolCallbacks)
                        .collect { emit(it) }
                }
            } catch (error: Throwable) {
                throw buildDetailedProviderError(error)
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse =
        withContext(Dispatchers.IO) {
            val selectedModel = AppConfig.instance.openAiModel
            val response =
                chatModel()
                    .call(
                        Prompt(
                            listOf(
                                UserMessage
                                    .builder()
                                    .text(prompt)
                                    .media(VisionSupport.media(image))
                                    .build(),
                            ),
                            OpenAiChatOptions.builder().model(selectedModel).build(),
                        ),
                    )
            ChatResponse(
                model = response.metadata.model,
                message =
                    Message(
                        role = "assistant",
                        content = response.result?.let { it.output.text.orEmpty() }.orEmpty(),
                    ),
                done = true,
                promptEvalCount = response.metadata.usage.promptTokens,
                evalCount = response.metadata.usage.completionTokens,
            )
        }

    override suspend fun embeddings(text: String): List<Double> = emptyList()

    override fun isConnected(): Boolean = AppConfig.instance.openAiApiKey.isNotBlank()

    override suspend fun checkConnection(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { getModels().isNotEmpty() }.getOrDefault(false)
        }

    override suspend fun getModels(): List<String> =
        withContext(Dispatchers.IO) {
            if (AppConfig.instance.openAiApiKey.isBlank()) {
                throw IllegalStateException("OpenAI API key is not configured")
            }
            try {
                OpenAiModelCatalog(::openAiClient).load(modelsUri())
            } catch (error: Throwable) {
                throw buildDetailedProviderError(error)
            }
        }

    internal suspend fun getModels(profile: ProviderProfile): List<String> =
        withContext(Dispatchers.IO) {
            requireUsableApiKey(profile.baseUrl, profile.apiKey)
            OpenAiModelCatalog { openAiClient(profile) }.load(modelsUri(profile))
        }

    internal suspend fun getModelDetails(
        profile: ProviderProfile,
        modelName: String,
    ): ShowResponse =
        ShowResponse(
            model = modelName,
            modifiedAt = "",
            details = ModelDetails(family = profile.name),
        )

    override suspend fun getModelDetails(modelName: String): ShowResponse =
        withContext(Dispatchers.IO) {
            ShowResponse(
                model = modelName,
                modifiedAt = "",
                details = ModelDetails(family = "openai-compatible"),
            )
        }

    private fun chatModel(profile: ProviderProfile? = null): ChatModel {
        val configuredBaseUrl = profile?.baseUrl ?: AppConfig.instance.openAiBaseUrl
        val apiKey = apiKeyFor(configuredBaseUrl, profile?.apiKey ?: AppConfig.instance.openAiApiKey)
        val baseUrl = OpenAiEndpointNormalizer.apiBaseUrl(configuredBaseUrl)
        val model = profile?.defaultModel ?: AppConfig.instance.openAiModel
        val options =
            OpenAiChatOptions
                .builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .model(model)
                .build()
        return OpenAiChatModel.builder().options(options).build()
    }

    private fun openAiClient(profile: ProviderProfile): OpenAIClient =
        OpenAiSetup.setupSyncClient(
            OpenAiEndpointNormalizer.apiBaseUrl(profile.baseUrl),
            apiKeyFor(profile.baseUrl, profile.apiKey),
            null,
            null,
            null,
            null,
            false,
            false,
            profile.defaultModel,
            Duration.ofSeconds(20),
            2,
            null,
            emptyMap(),
            ObservationRegistry.NOOP,
            null,
            emptyList(),
        )

    private fun apiKeyFor(
        baseUrl: String,
        configuredApiKey: String,
    ): String {
        requireUsableApiKey(baseUrl, configuredApiKey)
        return configuredApiKey.ifBlank { "none" }
    }

    private fun requireUsableApiKey(
        baseUrl: String,
        configuredApiKey: String,
    ) {
        if (configuredApiKey.isBlank() && OpenAiEndpointNormalizer.requiresApiKey(baseUrl)) {
            throw IllegalStateException("OpenAI API key is not configured")
        }
    }

    private fun openAiClient(): OpenAIClient =
        OpenAiSetup.setupSyncClient(
            apiBaseUrl(),
            AppConfig.instance.openAiApiKey,
            null,
            null,
            null,
            null,
            false,
            false,
            AppConfig.instance.openAiModel,
            Duration.ofSeconds(20),
            2,
            null,
            emptyMap(),
            ObservationRegistry.NOOP,
            null,
            emptyList(),
        )

    private fun apiBaseUrl(): String = OpenAiEndpointNormalizer.apiBaseUrl(AppConfig.instance.openAiBaseUrl)

    private fun modelsUri(): URI = URI.create("${apiBaseUrl()}/models")

    private fun modelsUri(profile: ProviderProfile): URI = URI.create("${OpenAiEndpointNormalizer.apiBaseUrl(profile.baseUrl)}/models")

    private fun buildDetailedProviderError(throwable: Throwable?): Throwable {
        if (throwable == null) return IllegalStateException("Unknown OpenAI chat model error")
        val detailedMessage = extractDetailedErrorMessage(throwable)
        return IllegalStateException(detailedMessage, throwable)
    }

    private fun extractDetailedErrorMessage(throwable: Throwable): String {
        var current: Throwable? = throwable
        var fallbackMessage: String? = throwable.message?.takeIf { it.isNotBlank() }
        while (current != null) {
            val responseBody = invokeResponseBodyMethod(current)
            if (!responseBody.isNullOrBlank()) {
                val statusText = current.message?.takeIf { it.isNotBlank() }
                return listOfNotNull(statusText, responseBody.trim()).joinToString(": ").trim()
            }
            if (!current.message.isNullOrBlank()) fallbackMessage = current.message
            current = current.cause
        }
        return fallbackMessage ?: "Unknown OpenAI chat model error"
    }

    private fun invokeResponseBodyMethod(throwable: Throwable): String? {
        val method: Method =
            runCatching { throwable.javaClass.getMethod("getResponseBodyAsString") }
                .getOrNull()
                ?: return null
        return runCatching { method.invoke(throwable) as? String }.getOrNull()
    }
}
