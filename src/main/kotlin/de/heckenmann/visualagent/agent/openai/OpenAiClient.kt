package de.heckenmann.visualagent.agent.openai

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * LLM provider implementation for OpenAI and OpenAI-compatible chat endpoints.
 */
@Component
class OpenAiClient(
    private val promptFactory: OpenAiPromptFactory,
) : LLMProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(messages: List<Message>): ChatResponse = chat(ChatRequestContext(messages = messages))

    override suspend fun chat(request: ChatRequestContext): ChatResponse =
        withContext(Dispatchers.IO) {
            val selectedModel = request.model ?: AppConfig.instance.openAiModel
            val responseResult = runCatching { chatModel().call(promptFactory.buildPrompt(request, selectedModel)) }
            if (responseResult.isFailure) throw buildDetailedProviderError(responseResult.exceptionOrNull())
            val response = responseResult.getOrThrow()
            ChatResponse(
                model = response.metadata?.model ?: selectedModel,
                message =
                    Message(
                        role = "assistant",
                        content =
                            response.result
                                ?.output
                                ?.text
                                .orEmpty(),
                    ),
                done = true,
                promptEvalCount =
                    response.metadata
                        ?.usage
                        ?.promptTokens
                        ?.toInt(),
                evalCount =
                    response.metadata
                        ?.usage
                        ?.completionTokens
                        ?.toInt(),
            )
        }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = stream(ChatRequestContext(messages = messages))

    override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> {
        val selectedModel = request.model ?: AppConfig.instance.openAiModel
        return flow {
            try {
                chatModel()
                    .stream(promptFactory.buildPrompt(request, selectedModel))
                    .asFlow()
                    .map { chunk ->
                        ChatResponse(
                            model = chunk.metadata?.model ?: selectedModel,
                            message =
                                Message(
                                    role = "assistant",
                                    content =
                                        chunk.result
                                            ?.output
                                            ?.text
                                            .orEmpty(),
                                ),
                            done = chunk.result?.metadata?.finishReason != null,
                            promptEvalCount =
                                chunk.metadata
                                    ?.usage
                                    ?.promptTokens
                                    ?.toInt(),
                            evalCount =
                                chunk.metadata
                                    ?.usage
                                    ?.completionTokens
                                    ?.toInt(),
                        )
                    }.collect { emit(it) }
            } catch (error: Throwable) {
                throw buildDetailedProviderError(error)
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse = throw UnsupportedOperationException("Vision not yet implemented in OpenAI bridge")

    override suspend fun embeddings(text: String): List<Double> = emptyList()

    override fun isConnected(): Boolean = AppConfig.instance.openAiApiKey.isNotBlank()

    override suspend fun checkConnection(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { getModels().isNotEmpty() }.getOrDefault(false)
        }

    override suspend fun getModels(): List<String> =
        withContext(Dispatchers.IO) {
            val configuredModel = AppConfig.instance.openAiModel
            if (AppConfig.instance.openAiApiKey.isBlank()) return@withContext listOf(configuredModel)
            runCatching {
                val request =
                    HttpRequest
                        .newBuilder(modelsUri())
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer ${AppConfig.instance.openAiApiKey}")
                        .header("Accept", "application/json")
                        .GET()
                        .build()
                val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() !in 200..299) return@runCatching listOf(configuredModel)
                val decoded = json.decodeFromString<ModelListResponse>(response.body())
                decoded.data
                    .map { it.id }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                    .ifEmpty { listOf(configuredModel) }
            }.getOrElse { listOf(configuredModel) }
        }

    override suspend fun getModelDetails(modelName: String): ShowResponse =
        withContext(Dispatchers.IO) {
            ShowResponse(
                model = modelName,
                modifiedAt = "",
                details = ModelDetails(family = "openai-compatible"),
            )
        }

    private fun chatModel(): ChatModel {
        if (AppConfig.instance.openAiApiKey.isBlank()) {
            throw IllegalStateException("OpenAI API key is not configured")
        }
        val api =
            OpenAiApi
                .builder()
                .apiKey(AppConfig.instance.openAiApiKey)
                .baseUrl(AppConfig.instance.openAiBaseUrl.trimEnd('/'))
                .build()
        return OpenAiChatModel.builder().openAiApi(api).build()
    }

    private fun modelsUri(): URI {
        val baseUrl = AppConfig.instance.openAiBaseUrl.trimEnd('/')
        val modelsUrl = if (baseUrl.endsWith("/v1")) "$baseUrl/models" else "$baseUrl/v1/models"
        return URI.create(modelsUrl)
    }

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

    @Serializable
    private data class ModelListResponse(
        val data: List<ModelInfo> = emptyList(),
    )

    @Serializable
    private data class ModelInfo(
        @SerialName("id")
        val id: String,
    )
}
