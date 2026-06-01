package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import de.heckenmann.visualagent.agent.ollama.OllamaToolRecovery
import de.heckenmann.visualagent.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.stereotype.Component
import java.lang.reflect.Method

/**
 * Represents OllamaClient.
 */
@Component
class OllamaClient(
    private val chatModel: ChatModel,
    private val ollamaApi: OllamaApi,
    private val promptFactory: OllamaPromptFactory,
    private val toolRecovery: OllamaToolRecovery,
) : LLMProvider {
    override suspend fun chat(messages: List<Message>): ChatResponse = chat(ChatRequestContext(messages = messages))

    override suspend fun chat(request: ChatRequestContext): ChatResponse =
        withContext(Dispatchers.IO) {
            val selectedModel = request.model ?: AppConfig.instance.ollamaModel
            val allowedFunctionNames = promptFactory.allowedFunctionNames(request, selectedModel)
            val responseResult = runCatching { chatModel.call(promptFactory.buildPrompt(request, selectedModel)) }
            if (responseResult.isFailure) {
                val error = responseResult.exceptionOrNull()
                if (error != null && isMissingFunctionCallbackError(error)) {
                    val recovered = toolRecovery.runUnknownToolRecovery(request, selectedModel, allowedFunctionNames, error)
                    return@withContext recovered
                        ?: ChatResponse(
                            model = selectedModel,
                            message = Message(role = "assistant", content = toolRecovery.buildToolListResponse(allowedFunctionNames)),
                            done = true,
                        )
                }
                throw buildDetailedProviderError(error)
            }
            val response = responseResult.getOrThrow()
            val output =
                response.result
                    ?.output
                    ?.text
                    .orEmpty()

            ChatResponse(
                model = response.metadata?.model ?: selectedModel,
                message =
                    Message(
                        role = "assistant",
                        content = output,
                    ),
                done = true,
                totalDuration = null,
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
        val selectedModel = request.model ?: AppConfig.instance.ollamaModel
        val allowedFunctionNames = promptFactory.allowedFunctionNames(request, selectedModel)
        return flow {
            try {
                chatModel
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
                if (!isMissingFunctionCallbackError(error)) throw buildDetailedProviderError(error)
                val recovered = toolRecovery.runUnknownToolRecovery(request, selectedModel, allowedFunctionNames, error)
                emit(
                    recovered
                        ?: ChatResponse(
                            model = selectedModel,
                            message = Message(role = "assistant", content = toolRecovery.buildToolListResponse(allowedFunctionNames)),
                            done = true,
                        ),
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Returns whether the throwable indicates a missing tool callback registration.
     *
     * @param throwable Captured chat/tool error
     * @return `true` for unknown-function callback failures
     */
    private fun isMissingFunctionCallbackError(throwable: Throwable): Boolean =
        throwable.message
            ?.contains("No function callback found for function name:")
            ?: false

    /**
     * Builds an exception with the most specific provider error message available.
     *
     * @param throwable Original provider error
     * @return Exception with expanded message including response body details when available
     */
    private fun buildDetailedProviderError(throwable: Throwable?): Throwable {
        if (throwable == null) return IllegalStateException("Unknown chat model error")
        val detailedMessage = extractDetailedErrorMessage(throwable)
        return IllegalStateException(detailedMessage, throwable)
    }

    /**
     * Extracts the most helpful error text from nested provider exceptions.
     *
     * @param throwable Root error from Spring AI/Ollama call
     * @return Human-readable message with server response details when present
     */
    private fun extractDetailedErrorMessage(throwable: Throwable): String {
        var current: Throwable? = throwable
        var fallbackMessage: String? = throwable.message?.takeIf { it.isNotBlank() }
        while (current != null) {
            val responseBody = invokeResponseBodyMethod(current)
            if (!responseBody.isNullOrBlank()) {
                val statusText = current.message?.takeIf { it.isNotBlank() }
                return listOfNotNull(statusText, responseBody.trim())
                    .joinToString(": ")
                    .trim()
            }
            if (!current.message.isNullOrBlank()) {
                fallbackMessage = current.message
            }
            current = current.cause
        }
        return fallbackMessage ?: "Unknown chat model error"
    }

    /**
     * Invokes `getResponseBodyAsString()` reflectively when available on Spring HTTP exceptions.
     *
     * @param throwable Candidate exception
     * @return Response body text or null
     */
    private fun invokeResponseBodyMethod(throwable: Throwable): String? {
        val method: Method =
            runCatching { throwable.javaClass.getMethod("getResponseBodyAsString") }
                .getOrNull()
                ?: return null
        return runCatching { method.invoke(throwable) as? String }.getOrNull()
    }

    override suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse {
        // Spring AI handles vision via UserMessage with Media
        // Implementation omitted for brevity in first step
        throw UnsupportedOperationException("Vision not yet implemented in Spring AI bridge")
    }

    override suspend fun embeddings(text: String): List<Double> =
        withContext(Dispatchers.IO) {
            try {
                val response = ollamaApi.embed(OllamaApi.EmbeddingsRequest(AppConfig.instance.ollamaModel, text))
                response
                    .embeddings()
                    .firstOrNull()
                    ?.map { value -> value.toDouble() }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    override fun isConnected(): Boolean = true

    override suspend fun checkConnection(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                ollamaApi.listModels()
                true
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun getModels(): List<String> =
        withContext(Dispatchers.IO) {
            val configuredModel = AppConfig.instance.ollamaModel
            try {
                val models =
                    ollamaApi
                        .listModels()
                        .models()
                        ?.mapNotNull { model -> model.name() }
                        ?.distinct()
                        ?: emptyList()
                if (models.isEmpty()) listOf(configuredModel) else models
            } catch (_: Exception) {
                listOf(configuredModel)
            }
        }

    override suspend fun getModelDetails(modelName: String): ShowResponse =
        withContext(Dispatchers.IO) {
            try {
                val response = ollamaApi.showModel(OllamaApi.ShowModelRequest(modelName))
                val details = response.details()
                ShowResponse(
                    model = modelName,
                    modifiedAt = response.modifiedAt()?.toString().orEmpty(),
                    parameters = response.parameters(),
                    template = response.template(),
                    system = response.system(),
                    license = response.license(),
                    details =
                        if (details != null) {
                            ModelDetails(
                                parentModel = details.parentModel(),
                                format = details.format(),
                                family = details.family(),
                                families = details.families(),
                                parameterSize = details.parameterSize(),
                                quantizationLevel = details.quantizationLevel(),
                            )
                        } else {
                            null
                        },
                )
            } catch (_: Exception) {
                ShowResponse(model = modelName, modifiedAt = "")
            }
        }
}

@Serializable
/**
 * Represents ShowResponse.
 */
data class ShowResponse(
    val model: String,
    val modifiedAt: String,
    val parameters: String? = null,
    val template: String? = null,
    val system: String? = null,
    val license: String? = null,
    val details: ModelDetails? = null,
    val messages: List<Message>? = null,
)

@Serializable
/**
 * Represents ModelDetails.
 */
data class ModelDetails(
    val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    val parameterSize: String? = null,
    val quantizationLevel: String? = null,
)
