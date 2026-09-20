package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderRuntimeConfig
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaChatOptions
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Vision and embedding operations for [OllamaClient].
 *
 * These side-channel operations do not participate in the tool-calling path and
 * are kept in a separate file so that [OllamaClient] stays under the project
 * line-of-code limit.
 */
internal class OllamaClientAuxiliary(
    private val chatModel: ChatModel,
    private val ollamaApi: OllamaApi,
    private val appConfig: ProviderRuntimeConfig,
) {
    /**
     * Sends an image and a prompt to the vision-capable chat model.
     *
     * @param image Raw image bytes
     * @param prompt Text prompt accompanying the image
     * @return Chat response with the model's interpretation
     */
    fun visionReactive(
        image: ByteArray,
        prompt: String,
        modelId: String = appConfig.ollamaModel,
    ): Mono<ChatResponse> =
        Mono
            .fromCallable {
                val response =
                    chatModel.call(
                        Prompt(
                            listOf(
                                UserMessage
                                    .builder()
                                    .text(prompt)
                                    .media(VisionSupport.media(image))
                                    .build(),
                            ),
                            OllamaChatOptions.builder().model(modelId).build(),
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
            }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Generates a single-vector embedding for the supplied text.
     *
     * @param text Input text to embed
     * @return Embedding vector or an empty list if the call fails
     */
    fun embeddingsReactive(text: String): Mono<List<Double>> = embeddingsReactive(text, appConfig.ollamaModel)

    /** Generates embeddings with the selected Ollama model. */
    fun embeddingsReactive(
        text: String,
        modelId: String,
    ): Mono<List<Double>> =
        Mono
            .fromCallable {
                try {
                    val response = ollamaApi.embed(OllamaApi.EmbeddingsRequest(modelId, text))
                    response
                        .embeddings()
                        .firstOrNull()
                        ?.map { value -> value.toDouble() }
                        ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }.subscribeOn(Schedulers.boundedElastic())
}
