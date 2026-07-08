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
 * Use cases: UC-0000002, UC-0000003, UC-0000007, UC-0000009, UC-0000010, UC-0000011,
 * UC-0000012, UC-0000027.
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
     * @see docs/usecases/uc_0000002_send_main_agent_message.md
     */
    suspend fun chat(messages: List<Message>): ChatResponse

    /**
     * Send a chat request with model, tool, and metadata context.
     *
     * @param request Complete request context for the provider
     * @return Complete chat response from the LLM
     * @throws Exception if the request fails or model is unavailable
     * @see docs/usecases/uc_0000002_send_main_agent_message.md
     * @see docs/usecases/uc_0000020_execute_tool_call.md
     */
    suspend fun chat(request: ChatRequestContext): ChatResponse = chat(request.messages)

    /**
     * Stream a chat response in real-time chunks.
     *
     * @param messages List of conversation messages
     * @return Flow of response chunks for real-time display
     * @see docs/usecases/uc_0000003_stream_main_agent_response.md
     */
    suspend fun stream(messages: List<Message>): Flow<ChatResponse>

    /**
     * Stream a chat request with model, tool, and metadata context.
     *
     * @param request Complete request context for the provider
     * @return Flow of response chunks for real-time display
     * @see docs/usecases/uc_0000003_stream_main_agent_response.md
     * @see docs/usecases/uc_0000020_execute_tool_call.md
     */
    suspend fun stream(request: ChatRequestContext): Flow<ChatResponse> = stream(request.messages)

    /**
     * Process an image with a vision-capable model.
     *
     * @param image Raw image bytes to analyze
     * @param prompt Text prompt for image analysis
     * @return Response describing the image content
     * @see docs/usecases/uc_0000027_analyze_workspace_file_via_tool.md
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
     * @see docs/usecases/uc_0000010_chat_with_ollama_provider.md
     */
    suspend fun embeddings(text: String): List<Double>

    /**
     * Check if the provider is currently connected and available.
     *
     * @return true if connected, false otherwise
     * @see docs/usecases/uc_0000012_check_provider_connectivity.md
     */
    fun isConnected(): Boolean

    /**
     * Perform an active connectivity check against the backing provider.
     *
     * @return true if the provider endpoint is reachable and responsive
     * @see docs/usecases/uc_0000012_check_provider_connectivity.md
     */
    suspend fun checkConnection(): Boolean

    /**
     * Get the list of available model names.
     *
     * @return List of model names
     * @see docs/usecases/uc_0000009_discover_available_models.md
     */
    suspend fun getModels(): List<String>

    /**
     * Gets model names for a specific configured provider.
     *
     * @param providerId Provider profile identifier
     * @return Selectable model names
     * @see docs/usecases/uc_0000009_discover_available_models.md
     */
    suspend fun getModels(providerId: String): List<String> = getModels()

    /**
     * Get detailed information about a specific model.
     *
     * @param modelName Name of the model to get details for
     * @return ShowResponse containing model details
     * @see docs/usecases/uc_0000009_discover_available_models.md
     */
    suspend fun getModelDetails(modelName: String): ShowResponse

    /**
     * Gets model details for a specific configured provider.
     *
     * @param providerId Provider profile identifier
     * @param modelName Provider-facing model identifier
     * @return Model details
     * @see docs/usecases/uc_0000009_discover_available_models.md
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
 * @property cancellationToken Optional token the provider can consult to honour user cancellation
 * @see docs/usecases/uc_0000002_send_main_agent_message.md
 * @see docs/usecases/uc_0000007_configure_session_provider_and_model.md
 * @see docs/usecases/uc_0000020_execute_tool_call.md
 * @see docs/usecases/uc_0000078_cancel_main_agent_response.md
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
    val cancellationToken: CancellationToken? = null,
)

/**
 * Provider-neutral model selection used by agents and request routing.
 *
 * @property provider Optional provider override; null inherits the active session provider
 * @property model Optional model override; null inherits the selected provider model
 * @property parameters Sampling and output limits for this model call
 * @see docs/usecases/uc_0000007_configure_session_provider_and_model.md
 * @see docs/usecases/uc_0000008_manage_provider_profiles.md
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
 * @see docs/usecases/uc_0000008_manage_provider_profiles.md
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
 * @see docs/usecases/uc_0000019_configure_agent_tools.md
 * @see docs/usecases/uc_0000020_execute_tool_call.md
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
 * @see docs/usecases/uc_0000020_execute_tool_call.md
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
 * @see docs/usecases/uc_0000020_execute_tool_call.md
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
 * Supported roles for the UI are `system`, `user`, `assistant`, `tool` and
 * `sub_agent`. The provider adapters only send `system`, `user` and
 * `assistant` to the LLM; `tool` and `sub_agent` messages are normalized
 * before being added to the request context.
 *
 * @property role Message role (`system`, `user`, `assistant`, `tool` or `sub_agent`)
 * @property content Text content of the message
 * @property metadata Optional JSON metadata payload for UI/system annotations
 * @property images Optional list of base64-encoded images (for vision)
 * @see docs/usecases/uc_0000002_send_main_agent_message.md
 * @see docs/usecases/uc_0000005_persist_and_reload_history.md
 * @see docs/usecases/uc_0000032_capture_canvas_image_history.md
 * @see docs/usecases/uc_0000020_execute_tool_call.md
 */
@Serializable
data class Message(
    val role: String,
    val content: String,
    val metadata: String? = null,
    val images: List<String>? = null,
    val id: String? = null,
)

/**
 * Request payload for chat API.
 *
 * @property model Model name to use
 * @property messages List of conversation messages
 * @property stream Enable streaming responses
 * @property options Optional model-specific options
 * @see docs/usecases/uc_0000002_send_main_agent_message.md
 * @see docs/usecases/uc_0000003_stream_main_agent_response.md
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
 * @see docs/usecases/uc_0000002_send_main_agent_message.md
 * @see docs/usecases/uc_0000003_stream_main_agent_response.md
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
