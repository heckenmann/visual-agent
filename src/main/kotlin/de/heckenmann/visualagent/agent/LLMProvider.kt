package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * LLM provider interface for chat, streaming, vision, and embedding capabilities.
 *
 * This interface abstracts the underlying LLM provider (Ollama Local, Ollama Cloud, etc.)
 * and provides a unified API for interacting with language models.
 *
 * @see OllamaClient for local Ollama implementation
 * @see OllamaCloudProvider for cloud implementation
 */
interface LLMProvider {
    /**
     * Send a chat message and receive a complete response.
     *
     * @param messages List of conversation messages with roles (system, user, assistant)
     * @return Complete chat response from the LLM
     * @throws Exception if the request fails or model is unavailable
     */
    suspend fun chat(messages: List<Message>): ChatResponse

    /**
     * Send a chat request with model, tool, and metadata context.
     *
     * @param request Complete request context for the provider
     * @return Complete chat response from the LLM
     * @throws Exception if the request fails or model is unavailable
     */
    suspend fun chat(request: ChatRequestContext): ChatResponse = chat(request.messages)

    /**
     * Stream a chat response in real-time chunks.
     *
     * @param messages List of conversation messages
     * @return Flow of response chunks for real-time display
     */
    suspend fun stream(messages: List<Message>): Flow<ChatResponse>

    /**
     * Stream a chat request with model, tool, and metadata context.
     *
     * @param request Complete request context for the provider
     * @return Flow of response chunks for real-time display
     */
    suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> = stream(request.messages)

    /**
     * Process an image with a vision-capable model.
     *
     * @param image Raw image bytes to analyze
     * @param prompt Text prompt for image analysis
     * @return Response describing the image content
     */
    suspend fun vision(
        image: ByteArray,
        prompt: String,
    ): ChatResponse

    /**
     * Generate embeddings for text.
     *
     * @param text Text to generate embeddings for
     * @return List of embedding values
     */
    suspend fun embeddings(text: String): List<Double>

    /**
     * Check if the provider is currently connected and available.
     *
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean

    /**
     * Perform an active connectivity check against the backing provider.
     *
     * @return true if the provider endpoint is reachable and responsive
     */
    suspend fun checkConnection(): Boolean

    /**
     * Get the list of available model names.
     *
     * @return List of model names
     */
    suspend fun getModels(): List<String>

    /**
     * Gets model names for a specific configured provider.
     *
     * @param providerId Provider profile identifier
     * @return Selectable model names
     */
    suspend fun getModels(providerId: String): List<String> = getModels()

    /**
     * Get detailed information about a specific model.
     *
     * @param modelName Name of the model to get details for
     * @return ShowResponse containing model details
     */
    suspend fun getModelDetails(modelName: String): ShowResponse

    /**
     * Gets model details for a specific configured provider.
     *
     * @param providerId Provider profile identifier
     * @param modelName Provider-facing model identifier
     * @return Model details
     */
    suspend fun getModelDetails(
        providerId: String,
        modelName: String,
    ): ShowResponse = getModelDetails(modelName)
}

/**
 * Complete provider request context for chat calls.
 *
 * @property messages Ordered conversation messages
 * @property model Optional model override; defaults to the configured model when null
 * @property enabledTools Tool IDs that may be exposed to the model for this request
 * @property metadata Additional provider-neutral execution context
 */
data class ChatRequestContext(
    val messages: List<Message>,
    val provider: String? = null,
    val model: String? = null,
    val variant: String? = null,
    val parameters: ModelParameters = ModelParameters(),
    val options: Map<String, String> = emptyMap(),
    val enabledTools: Set<ToolId> = emptySet(),
    val metadata: Map<String, Any> = emptyMap(),
    val providerProfile: ProviderProfile? = null,
)

/**
 * Provider-neutral model selection used by agents and request routing.
 *
 * @property provider Optional provider override; null inherits the active session provider
 * @property model Optional model override; null inherits the selected provider model
 * @property parameters Sampling and output limits for this model call
 */
data class ModelSelection(
    val provider: String? = null,
    val model: String? = null,
    val variant: String? = null,
    val parameters: ModelParameters = ModelParameters(),
    val options: Map<String, String> = emptyMap(),
)

/**
 * Provider-neutral generation parameters supported by both configured backends.
 *
 * @property temperature Sampling temperature in the range 0.0 through 2.0
 * @property topP Nucleus sampling probability in the range 0.0 through 1.0
 * @property maxTokens Maximum number of generated tokens
 */
data class ModelParameters(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
) {
    init {
        require(temperature == null || temperature in 0.0..2.0) { "temperature must be between 0.0 and 2.0" }
        require(topP == null || topP in 0.0..1.0) { "topP must be between 0.0 and 1.0" }
        require(maxTokens == null || maxTokens > 0) { "maxTokens must be greater than zero" }
    }
}

/**
 * Stable identifier for an application tool.
 *
 * @property value External tool ID such as `file:read` or `ui`
 */
@JvmInline
value class ToolId(
    val value: String,
)

/**
 * Provider-neutral description of a callable tool.
 *
 * @property id Stable application tool ID
 * @property name Provider-safe function name exposed to Spring AI
 * @property description Description used by the model to decide when to call the tool
 * @property inputSchema JSON schema for the tool input
 */
data class ToolDefinition(
    val id: ToolId,
    val name: String,
    val description: String,
    val inputSchema: String,
)

/**
 * Structured result returned by a tool execution.
 *
 * @property toolId Tool that produced the result
 * @property success Whether the tool completed successfully
 * @property content Human-readable result payload
 * @property error Optional error message when execution failed
 */
@Serializable
data class ToolResult(
    val toolId: String,
    val success: Boolean,
    val content: String,
    val error: String? = null,
)

/**
 * Single provider-neutral message in a conversation.
 *
 * @property role Message role (system, user, or assistant)
 * @property content Text content of the message
 * @property metadata Optional JSON metadata payload for UI/system annotations
 * @property images Optional list of base64-encoded images (for vision)
 */
@Serializable
data class Message(
    val role: String,
    val content: String,
    val metadata: String? = null,
    val images: List<String>? = null,
)

/**
 * Request payload for chat API.
 *
 * @property model Model name to use
 * @property messages List of conversation messages
 * @property stream Enable streaming responses
 * @property options Optional model-specific options
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false,
    val options: Map<String, String>? = null,
)

/**
 * Response from chat API.
 *
 * @property model Model that generated the response
 * @property message The assistant's response message
 * @property done Whether this is the final chunk
 * @property totalDuration Total processing time in nanoseconds
 * @property promptEvalCount Number of tokens in the prompt
 * @property evalCount Number of tokens in the response
 */
@Serializable
data class ChatResponse(
    val model: String,
    val message: Message,
    val done: Boolean,
    val totalDuration: Long? = null,
    val promptEvalCount: Int? = null,
    val evalCount: Int? = null,
)
