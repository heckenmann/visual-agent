package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import de.heckenmann.visualagent.agent.ollama.OllamaToolRecovery
import de.heckenmann.visualagent.agent.ollama.createOllamaApi
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.error.ErrorMessageMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.stereotype.Component

/**
 * Spring AI backed LLM provider for Ollama endpoints and Ollama-compatible profiles.
 *
 * Chat, streaming, vision, embedding, and metadata operations are split across
 * this class and the [OllamaClientAuxiliary] / [OllamaClientOps] helpers to keep
 * the per-file line count within the project limits.
 */
@Component
class OllamaClient(
    private val chatModel: ChatModel,
    private val ollamaApi: OllamaApi,
    private val promptFactory: OllamaPromptFactory,
    private val toolRecovery: OllamaToolRecovery,
    private val toolRegistry: ToolRegistry,
    private val appConfig: AppConfigBean = AppConfigBean(),
) : LLMProvider {
    private val logger = KotlinLogging.logger {}
    private val auxiliary = OllamaClientAuxiliary(chatModel, ollamaApi, appConfig)
    private val ops = OllamaClientOps(ollamaApi, appConfig)

    override suspend fun chat(messages: List<Message>): ChatResponse = chat(ChatRequestContext(messages = messages))

    override suspend fun chat(request: ChatRequestContext): ChatResponse =
        withContext(Dispatchers.IO) {
            request.cancellationToken?.throwIfCancelled()
            val selectedModel = request.model ?: appConfig.ollamaModel
            val allowedFunctionNames = promptFactory.allowedFunctionNames(request, selectedModel)
            val supportsTools = request.modelCapabilities.contains("tools")
            val toolsEnabled = request.enabledTools.isNotEmpty()
            logger.debug {
                "Ollama chat: model=$selectedModel, supportsTools=$supportsTools, toolsEnabled=$toolsEnabled"
            }
            val responseResult =
                runCatching {
                    if (supportsTools && toolsEnabled) {
                        val prompt = promptFactory.buildPrompt(request, selectedModel)
                        val model = chatModelFor(request)
                        ToolCallingLoop()
                            .run(
                                model,
                                prompt,
                                request.cancellationToken,
                                toolRegistry.functionCallbacks(
                                    request.enabledTools,
                                    request.metadata + mapOf("model" to selectedModel),
                                ),
                            )
                    } else {
                        OllamaToollessChat.execute(
                            ollamaApi = ollamaApiFor(request),
                            promptFactory = promptFactory,
                            request = request,
                            selectedModel = selectedModel,
                        )
                    }
                }
            if (responseResult.isFailure) {
                val error = responseResult.exceptionOrNull()
                if (error is kotlinx.coroutines.CancellationException) throw error
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
            responseResult.getOrThrow()
        }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = stream(ChatRequestContext(messages = messages))

    override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> {
        val selectedModel = request.model ?: appConfig.ollamaModel
        val allowedFunctionNames = promptFactory.allowedFunctionNames(request, selectedModel)
        val prompt = promptFactory.buildPrompt(request, selectedModel)
        val supportsTools = request.modelCapabilities.contains("tools")
        val toolsEnabled = request.enabledTools.isNotEmpty()
        val toolCallbacks =
            if (!supportsTools || !toolsEnabled) {
                emptyList()
            } else {
                toolRegistry.functionCallbacks(
                    request.enabledTools,
                    request.metadata + mapOf("model" to selectedModel),
                )
            }
        return flow {
            try {
                request.cancellationToken?.throwIfCancelled()
                if (toolCallbacks.isEmpty()) {
                    OllamaToollessChat
                        .stream(
                            ollamaApi = ollamaApiFor(request),
                            promptFactory = promptFactory,
                            request = request,
                            selectedModel = selectedModel,
                        ).collect { emit(it) }
                } else {
                    val model = chatModelFor(request)
                    ToolCallingLoop()
                        .runStream(model, prompt, request.cancellationToken, toolCallbacks)
                        .collect { emit(it) }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
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
        val userFacing = ErrorMessageMapper.map(throwable)
        return IllegalStateException("${userFacing.summary}: ${userFacing.detail}", throwable)
    }

    override suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse = auxiliary.vision(image, prompt)

    override suspend fun embeddings(text: String): List<Double> = auxiliary.embeddings(text)

    override fun isConnected(): Boolean = ops.isConnected()

    override suspend fun checkConnection(): Boolean = ops.checkConnection()

    override suspend fun getModels(): List<String> = ops.getModels()

    internal suspend fun getModels(profile: ProviderProfile): List<String> = ops.getModels(profile)

    internal suspend fun getModelDetails(
        profile: ProviderProfile,
        modelName: String,
    ): ShowResponse = ops.getModelDetails(profile, modelName)

    override suspend fun getModelDetails(modelName: String): ShowResponse = ops.getModelDetails(modelName)

    private fun chatModelFor(request: ChatRequestContext): ChatModel {
        val profile = request.providerProfile ?: return chatModel
        return OllamaChatModel
            .builder()
            .ollamaApi(createOllamaApi(profile, appConfig))
            .options(OllamaChatOptions.builder().model(request.model ?: profile.defaultModel).build())
            .build()
    }

    private fun ollamaApiFor(request: ChatRequestContext): OllamaApi =
        request.providerProfile
            ?.let { createOllamaApi(it, appConfig) }
            ?: ollamaApi
}
