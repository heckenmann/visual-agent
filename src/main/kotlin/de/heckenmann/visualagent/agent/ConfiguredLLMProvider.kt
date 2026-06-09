package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.config.AppConfig
import kotlinx.coroutines.flow.Flow
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Primary provider facade that delegates model operations to the configured backend.
 */
@Primary
@Component
class ConfiguredLLMProvider(
    private val ollamaClient: OllamaClient,
    private val openAiClient: OpenAiClient,
) : LLMProvider {
    override suspend fun chat(messages: List<Message>): ChatResponse = activeProvider().chat(messages)

    override suspend fun chat(request: ChatRequestContext): ChatResponse = activeProvider().chat(request.withActiveProviderModel())

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = activeProvider().stream(messages)

    override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> =
        activeProvider().stream(request.withActiveProviderModel())

    override suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse = activeProvider().vision(image, prompt)

    override suspend fun embeddings(text: String): List<Double> = activeProvider().embeddings(text)

    override fun isConnected(): Boolean = activeProvider().isConnected()

    override suspend fun checkConnection(): Boolean = activeProvider().checkConnection()

    override suspend fun getModels(): List<String> = activeProvider().getModels()

    override suspend fun getModelDetails(modelName: String): ShowResponse = activeProvider().getModelDetails(modelName)

    private fun activeProvider(): LLMProvider =
        when (AppConfig.instance.normalizedProvider()) {
            "openai" -> openAiClient
            else -> ollamaClient
        }

    private fun ChatRequestContext.withActiveProviderModel(): ChatRequestContext =
        if (model != null) {
            this
        } else {
            copy(model = AppConfig.instance.activeModel())
        }
}
