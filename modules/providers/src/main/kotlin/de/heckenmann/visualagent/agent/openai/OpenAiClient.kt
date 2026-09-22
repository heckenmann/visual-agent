package de.heckenmann.visualagent.agent.openai

import com.openai.client.OpenAIClient
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ProviderTurnResponseMapper
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.ToolCallingLoop
import de.heckenmann.visualagent.agent.VisionSupport
import de.heckenmann.visualagent.agent.provider.DefaultProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.ProviderEnvironmentCredentials
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.setup.OpenAiSetup
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.lang.reflect.Method
import java.net.URI
import java.time.Duration

/**
 * LLM provider implementation for OpenAI and OpenAI-compatible chat endpoints.
 */
@Component
class OpenAiClient(
    private val promptFactory: OpenAiPromptFactory,
    private val toolRegistry: ProviderToolCallbacks,
    private val appConfig: ProviderRuntimeConfig = DefaultProviderRuntimeConfig(),
) : LLMProvider {
    override fun chatReactive(messages: List<Message>): Mono<ChatResponse> = chatReactive(ChatRequestContext(messages = messages))

    override fun chatReactive(request: ChatRequestContext): Mono<ChatResponse> {
        val selectedModel = request.model ?: appConfig.openAiModel
        val prompt = promptFactory.buildPrompt(request, selectedModel)
        val model = chatModel(request.providerProfile, selectedModel)
        return ToolCallingLoop()
            .runReactive(
                model,
                prompt,
                request.cancellationToken,
                toolCallbacks(request, selectedModel),
                toolRegistry,
                request.contextWindow.withRequestedOutput(request.parameters.maxTokens),
            ).onErrorMap(::buildDetailedProviderError)
    }

    override fun streamReactive(messages: List<Message>): Flux<ChatResponse> = streamReactive(ChatRequestContext(messages = messages))

    override fun streamReactive(request: ChatRequestContext): Flux<ChatResponse> {
        val selectedModel = request.model ?: appConfig.openAiModel
        val prompt = promptFactory.buildPrompt(request, selectedModel)
        val model = chatModel(request.providerProfile, selectedModel)
        val toolCallbacks = toolCallbacks(request, selectedModel)
        return Flux
            .defer {
                request.cancellationToken?.throwIfCancelled()
                if (toolCallbacks.isEmpty()) {
                    model.stream(prompt).map { chunk ->
                        ProviderTurnResponseMapper
                            .fromSpring(chunk)
                            .let { turn -> if (turn.model.isBlank()) turn.copy(model = selectedModel) else turn }
                            .let(ProviderTurnResponseMapper::toChatResponse)
                    }
                } else {
                    ToolCallingLoop()
                        .runStreamReactive(
                            model,
                            prompt,
                            request.cancellationToken,
                            toolCallbacks,
                            toolRegistry,
                            request.contextWindow.withRequestedOutput(request.parameters.maxTokens),
                        )
                }
            }.onErrorMap(::buildDetailedProviderError)
    }

    private fun toolCallbacks(
        request: ChatRequestContext,
        selectedModel: String,
    ) = if (request.enabledTools.isEmpty()) {
        emptyList()
    } else {
        toolRegistry.functionCallbacks(
            request.enabledTools,
            request.metadata + mapOf("model" to selectedModel, "provider" to "openai") +
                (request.cancellationToken?.let { mapOf("cancellationToken" to it) } ?: emptyMap()),
        )
    }

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
    ): Mono<ChatResponse> = visionReactive(image, prompt, appConfig.openAiModel)

    override fun visionReactive(
        image: ByteArray,
        prompt: String,
        modelId: String,
    ): Mono<ChatResponse> =
        Mono
            .fromCallable {
                val response =
                    chatModel(modelId = modelId)
                        .call(
                            Prompt(
                                listOf(
                                    UserMessage
                                        .builder()
                                        .text(prompt)
                                        .media(VisionSupport.media(image))
                                        .build(),
                                ),
                                OpenAiChatOptions.builder().model(modelId).build(),
                            ),
                        )
                ProviderTurnResponseMapper.toChatResponse(ProviderTurnResponseMapper.fromSpring(response))
            }.subscribeOn(Schedulers.boundedElastic())

    override fun embeddingsReactive(text: String): Mono<List<Double>> = Mono.just(emptyList())

    override fun embeddingsReactive(
        text: String,
        modelId: String,
    ): Mono<List<Double>> = embeddingsReactive(text)

    override fun isConnected(): Boolean = appConfig.openAiApiKey.isNotBlank()

    override fun checkConnectionReactive(): Mono<Boolean> =
        getModelsReactive()
            .map(List<String>::isNotEmpty)
            .onErrorReturn(false)

    /** Checks connectivity using the selected OpenAI-compatible provider profile. */
    fun checkConnectionReactive(profile: ProviderProfile): Mono<Boolean> =
        getModelsReactive(profile)
            .map { true }
            .onErrorReturn(false)

    override fun getModelsReactive(): Mono<List<String>> =
        Mono
            .fromCallable {
                if (appConfig.openAiApiKey.isBlank()) {
                    throw IllegalStateException("OpenAI API key is not configured")
                }
                try {
                    OpenAiModelCatalog(::openAiClient).load(modelsUri())
                } catch (error: Throwable) {
                    throw buildDetailedProviderError(error)
                }
            }.subscribeOn(Schedulers.boundedElastic())

    override fun getModelsReactive(profile: ProviderProfile): Mono<List<String>> =
        Mono
            .fromCallable {
                requireUsableApiKey(profile.baseUrl, ProviderEnvironmentCredentials.openAiApiKey(profile))
                OpenAiModelCatalog { openAiClient(profile) }.load(modelsUri(profile))
            }.subscribeOn(Schedulers.boundedElastic())

    internal fun getModelDetailsReactive(
        profile: ProviderProfile,
        modelName: String,
    ): Mono<ShowResponse> =
        Mono.just(
            ShowResponse(
                model = modelName,
                modifiedAt = "",
                details = ModelDetails(family = profile.name),
            ),
        )

    override fun getModelDetailsReactive(modelName: String): Mono<ShowResponse> =
        Mono.fromCallable {
            ShowResponse(
                model = modelName,
                modifiedAt = "",
                details = ModelDetails(family = "openai-compatible"),
            )
        }

    private fun chatModel(
        profile: ProviderProfile? = null,
        modelId: String? = null,
    ): ChatModel {
        val configuredBaseUrl = profile?.baseUrl ?: appConfig.openAiBaseUrl
        val apiKey =
            apiKeyFor(
                configuredBaseUrl,
                profile?.let(ProviderEnvironmentCredentials::openAiApiKey) ?: appConfig.openAiApiKey,
            )
        val baseUrl = OpenAiEndpointNormalizer.apiBaseUrl(configuredBaseUrl)
        val model = modelId ?: profile?.defaultModel ?: appConfig.openAiModel
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
            apiKeyFor(profile.baseUrl, ProviderEnvironmentCredentials.openAiApiKey(profile)),
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
            appConfig.openAiApiKey,
            null,
            null,
            null,
            null,
            false,
            false,
            appConfig.openAiModel,
            Duration.ofSeconds(20),
            2,
            null,
            emptyMap(),
            ObservationRegistry.NOOP,
            null,
            emptyList(),
        )

    private fun apiBaseUrl(): String = OpenAiEndpointNormalizer.apiBaseUrl(appConfig.openAiBaseUrl)

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
