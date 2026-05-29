package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.serialization.Serializable
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component

@Component
class OllamaClient(
    private val chatModel: ChatModel,
) : LLMProvider {

    override suspend fun chat(messages: List<Message>): ChatResponse {
        val springMessages = messages.map { msg ->
            when (msg.role) {
                "system" -> SystemMessage(msg.content)
                "user" -> UserMessage(msg.content)
                else -> AssistantMessage(msg.content)
            }
        }

        val prompt = Prompt(springMessages)
        val response = chatModel.call(prompt)
        val result = response.result.output

        return ChatResponse(
            model = AppConfig.instance.ollamaModel,
            message = Message(
                role = "assistant",
                content = result.content
            ),
            done = true
        )
    }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> {
        val springMessages = messages.map { msg ->
            when (msg.role) {
                "system" -> SystemMessage(msg.content)
                "user" -> UserMessage(msg.content)
                else -> AssistantMessage(msg.content)
            }
        }

        val prompt = Prompt(springMessages)
        return chatModel.stream(prompt).asFlow().map { chunk ->
            ChatResponse(
                model = AppConfig.instance.ollamaModel,
                message = Message(
                    role = "assistant",
                    content = chunk.result.output.content
                ),
                done = chunk.result.metadata.finishReason != null
            )
        }
    }

    override suspend fun vision(image: ByteArray, prompt: String): ChatResponse {
        // Spring AI handles vision via UserMessage with Media
        // Implementation omitted for brevity in first step
        throw UnsupportedOperationException("Vision not yet implemented in Spring AI bridge")
    }

    override suspend fun embeddings(text: String): List<Double> {
        return emptyList() // Needs EmbeddingModel injection
    }

    override fun isConnected(): Boolean = true

    suspend fun checkConnection(): Boolean {
        return try {
            // We can't easily check connection with Spring AI ChatModel without making a call
            // But we can use the underlying API if needed.
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getModels(): List<String> {
        return listOf(AppConfig.instance.ollamaModel)
    }

    override suspend fun getModelDetails(modelName: String): ShowResponse {
        return ShowResponse(modelName, "")
    }
}

@Serializable
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
data class ModelDetails(
    val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    val parameterSize: String? = null,
    val quantizationLevel: String? = null,
)
