package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import de.heckenmann.visualagent.agent.ollama.OllamaToolRecovery
import de.heckenmann.visualagent.agent.ollama.createOllamaApi
import de.heckenmann.visualagent.agent.ollama.fetchModelCapabilitiesReactive
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.supportsToolCalling
import mu.KotlinLogging
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

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
    private val toolRegistry: ProviderToolCallbacks,
    private val appConfig: ProviderRuntimeConfig,
) : LLMProvider {
    private val logger = KotlinLogging.logger {}
    private val auxiliary = OllamaClientAuxiliary(chatModel, ollamaApi, appConfig)
    private val ops = OllamaClientOps(ollamaApi, appConfig)

    internal fun getModelCapabilitiesReactive(profile: ProviderProfile): Mono<Map<String, Set<String>>> =
        fetchModelCapabilitiesReactive(
            profile,
            de.heckenmann.visualagent.agent.ollama
                .createOllamaApi(profile, appConfig),
        )

    override fun chatReactive(messages: List<Message>): Mono<ChatResponse> = chatReactive(ChatRequestContext(messages = messages))

    override fun chatReactive(request: ChatRequestContext): Mono<ChatResponse> {
        val selectedModel = request.model ?: appConfig.ollamaModel
        val allowedFunctionNames = promptFactory.allowedFunctionNames(request, selectedModel)
        return Mono
            .defer {
                request.cancellationToken?.throwIfCancelled()
                val supportsTools = request.supportsToolCalling()
                val toolsEnabled = request.enabledTools.isNotEmpty()
                logger.debug {
                    "Ollama chat: model=$selectedModel, supportsTools=$supportsTools, toolsEnabled=$toolsEnabled"
                }
                if (supportsTools && toolsEnabled) {
                    val prompt = promptFactory.buildPrompt(request, selectedModel)
                    val model = chatModelFor(request)
                    ToolCallingLoop(outputLimitUpdater = promptFactory::updateOutputLimit)
                        .runReactive(
                            model,
                            prompt,
                            request.cancellationToken,
                            toolCallbacks(request, selectedModel),
                            toolRegistry,
                            request.contextWindow.withRequestedOutput(request.parameters.maxTokens),
                            request.metadata,
                        )
                } else {
                    Mono
                        .fromCallable {
                            OllamaToollessChat.execute(
                                ollamaApi = ollamaApiFor(request),
                                promptFactory = promptFactory,
                                request = request,
                                selectedModel = selectedModel,
                            )
                        }.subscribeOn(Schedulers.boundedElastic())
                }
            }.onErrorResume { error ->
                if (error is kotlinx.coroutines.CancellationException) {
                    Mono.error(error)
                } else if (isMissingFunctionCallbackError(error)) {
                    Mono
                        .fromCallable {
                            recoverUnknownTool(request, selectedModel, allowedFunctionNames, error)
                        }.subscribeOn(Schedulers.boundedElastic())
                } else {
                    Mono.error(buildDetailedProviderError(error))
                }
            }
    }

    override fun streamReactive(messages: List<Message>): Flux<ChatResponse> = streamReactive(ChatRequestContext(messages = messages))

    override fun streamReactive(request: ChatRequestContext): Flux<ChatResponse> {
        val selectedModel = request.model ?: appConfig.ollamaModel
        val allowedFunctionNames = promptFactory.allowedFunctionNames(request, selectedModel)
        val prompt = promptFactory.buildPrompt(request, selectedModel)
        val supportsTools = request.supportsToolCalling()
        val toolsEnabled = request.enabledTools.isNotEmpty()
        val toolCallbacks = if (!supportsTools || !toolsEnabled) emptyList() else toolCallbacks(request, selectedModel)
        return Flux
            .defer {
                request.cancellationToken?.throwIfCancelled()
                if (toolCallbacks.isEmpty()) {
                    OllamaToollessChat
                        .streamReactive(
                            ollamaApi = ollamaApiFor(request),
                            promptFactory = promptFactory,
                            request = request,
                            selectedModel = selectedModel,
                        )
                } else {
                    val model = chatModelFor(request)
                    ToolCallingLoop(outputLimitUpdater = promptFactory::updateOutputLimit)
                        .runStreamReactive(
                            model,
                            prompt,
                            request.cancellationToken,
                            toolCallbacks,
                            toolRegistry,
                            request.contextWindow.withRequestedOutput(request.parameters.maxTokens),
                            request.metadata,
                        )
                }
            }.onErrorResume { error ->
                when {
                    error is kotlinx.coroutines.CancellationException -> Flux.error(error)
                    !isMissingFunctionCallbackError(error) -> Flux.error(buildDetailedProviderError(error))
                    else ->
                        Mono
                            .fromCallable {
                                recoverUnknownTool(request, selectedModel, allowedFunctionNames, error)
                            }.subscribeOn(Schedulers.boundedElastic())
                            .flux()
                }
            }
    }

    private fun toolCallbacks(
        request: ChatRequestContext,
        selectedModel: String,
    ) = toolRegistry.functionCallbacks(
        request.enabledTools,
        request.metadata + mapOf("model" to selectedModel) +
            (request.cancellationToken?.let { mapOf("cancellationToken" to it) } ?: emptyMap()),
    )

    private fun recoverUnknownTool(
        request: ChatRequestContext,
        selectedModel: String,
        allowedFunctionNames: List<String>,
        error: Throwable,
    ): ChatResponse =
        toolRecovery.runUnknownToolRecovery(request, selectedModel, allowedFunctionNames, error)
            ?: ChatResponse(
                model = selectedModel,
                message =
                    Message(
                        role = "assistant",
                        content = toolRecovery.buildToolListResponse(allowedFunctionNames),
                    ),
                done = true,
            )

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
        val userFacing = ProviderErrorMessages.userFacingError(throwable)
        return IllegalStateException("${userFacing.summary}: ${userFacing.detail}", throwable)
    }

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
    ): Mono<ChatResponse> = auxiliary.visionReactive(image, prompt)

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
        modelId: String,
    ): Mono<ChatResponse> = auxiliary.visionReactive(image, prompt, modelId)

    override fun embeddingsReactive(text: String): Mono<List<Double>> = auxiliary.embeddingsReactive(text)

    override fun embeddingsReactive(
        text: String,
        modelId: String,
    ): Mono<List<Double>> = auxiliary.embeddingsReactive(text, modelId)

    override fun isConnected(): Boolean = ops.isConnected()

    override fun checkConnectionReactive(): Mono<Boolean> = ops.checkConnectionReactive()

    /** Checks connectivity using the selected Ollama provider profile. */
    fun checkConnectionReactive(profile: ProviderProfile): Mono<Boolean> = ops.checkConnectionReactive(profile)

    override fun getModelsReactive(): Mono<List<String>> = ops.getModelsReactive()

    override fun getModelsReactive(profile: ProviderProfile): Mono<List<String>> = ops.getModelsReactive(profile)

    internal fun getModelDetailsReactive(
        profile: ProviderProfile,
        modelName: String,
    ): Mono<ShowResponse> = ops.getModelDetailsReactive(profile, modelName)

    override fun getModelDetailsReactive(modelName: String): Mono<ShowResponse> = ops.getModelDetailsReactive(modelName)

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
