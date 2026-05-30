package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.function.FunctionCallingOptions
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.stereotype.Component

/**
 * Spring AI-backed LLM provider for local Ollama chat, tools, embeddings, and model metadata.
 */
@Component
class OllamaClient(
    private val chatModel: ChatModel,
    private val ollamaApi: OllamaApi,
    private val toolRegistry: ToolRegistry,
) : LLMProvider {
    override suspend fun chat(messages: List<Message>): ChatResponse = chat(ChatRequestContext(messages = messages))

    override suspend fun chat(request: ChatRequestContext): ChatResponse =
        withContext(Dispatchers.IO) {
            val selectedModel = request.model ?: AppConfig.instance.ollamaModel
            val response = chatModel.call(buildPrompt(request, selectedModel))
            val output =
                response.result
                    ?.output
                    ?.content
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
                        ?.generationTokens
                        ?.toInt(),
            )
        }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = stream(ChatRequestContext(messages = messages))

    override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> {
        val selectedModel = request.model ?: AppConfig.instance.ollamaModel
        return chatModel
            .stream(buildPrompt(request, selectedModel))
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
                                    ?.content
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
                            ?.generationTokens
                            ?.toInt(),
                )
            }.flowOn(Dispatchers.IO)
    }

    private fun buildPrompt(
        request: ChatRequestContext,
        selectedModel: String,
    ): Prompt {
        val callbacks =
            toolRegistry.functionCallbacks(
                enabledTools = request.enabledTools,
                context = request.metadata + mapOf("model" to selectedModel),
            )
        val options =
            FunctionCallingOptions
                .builder()
                .model(selectedModel)
                .functionCallbacks(callbacks)
                .functions(callbacks.map { it.name }.toSet())
                .build()
        return Prompt(
            request.messages.map { msg ->
                when (msg.role) {
                    "system" -> SystemMessage(msg.content)
                    "assistant" -> AssistantMessage(msg.content)
                    else -> UserMessage(msg.content)
                }
            },
            options,
        )
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
