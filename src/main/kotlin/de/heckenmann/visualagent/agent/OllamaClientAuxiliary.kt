package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.config.AppConfigBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaChatOptions

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
    private val appConfig: AppConfigBean,
) {
    /**
     * Sends an image and a prompt to the vision-capable chat model.
     *
     * @param image Raw image bytes
     * @param prompt Text prompt accompanying the image
     * @return Chat response with the model's interpretation
     */
    suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse =
        withContext(Dispatchers.IO) {
            val selectedModel = appConfig.ollamaModel
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
                        OllamaChatOptions.builder().model(selectedModel).build(),
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

    /**
     * Generates a single-vector embedding for the supplied text.
     *
     * @param text Input text to embed
     * @return Embedding vector or an empty list if the call fails
     */
    suspend fun embeddings(text: String): List<Double> =
        withContext(Dispatchers.IO) {
            try {
                val response = ollamaApi.embed(OllamaApi.EmbeddingsRequest(appConfig.ollamaModel, text))
                response
                    .embeddings()
                    .firstOrNull()
                    ?.map { value -> value.toDouble() }
                    ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
}
