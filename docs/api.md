# API Reference

## Ollama Integration

### REST API Endpoints

| Endpoint | Method | Description | Implementation Status |
|----------|--------|-------------|-----------------------|
| `/api/chat` | POST | Chat conversation | Implemented |
| `/api/embeddings` | POST | Generate embeddings | Implemented (has bug) |
| `/api/tags` | GET | Available models | Implemented — used by `checkConnection()` and `getModels()` |
| `/api/show` | POST | Model details | Implemented — used by `getModelDetails()` |

### Implemented Client: OllamaClient

Located at `de.heckenmann.visualagent.agent.OllamaClient`.

```kotlin
class OllamaClient(
    private val chatModel: ChatModel,
    private val ollamaApi: OllamaApi,
    private val toolRegistry: ToolRegistry,
) : LLMProvider
```

#### chat()

```kotlin
override suspend fun chat(messages: List<Message>): ChatResponse
override suspend fun chat(request: ChatRequestContext): ChatResponse
```

Sends a full chat request and returns the complete response. The request-based overload attaches Spring AI function callbacks for enabled tools.

#### stream()

```kotlin
override suspend fun stream(messages: List<Message>): Flow<ChatResponse>
override suspend fun stream(request: ChatRequestContext): Flow<ChatResponse>
```

Streams a chat response in real-time chunks through Spring AI.

#### vision()

```kotlin
override suspend fun vision(image: ByteArray, prompt: String): ChatResponse
```

**Known Issue**: Hardcodes model `"llava"` instead of using `AppConfig.instance.ollamaModel`.

#### embeddings()

```kotlin
override suspend fun embeddings(text: String): List<Double>
```

Uses Spring AI's Ollama API embedding request object.

#### checkConnection()

```kotlin
suspend fun checkConnection(): Boolean
```

Calls `GET /api/tags` to verify Ollama is reachable. Sets the `connected` flag. Called on startup by `MainWindow.checkConnection()`.

#### getModels()

```kotlin
override suspend fun getModels(): List<String>
```

Calls `GET /api/tags` and extracts model names from the JSON response. Called by `SessionPanel.refreshModels()` via `setOllamaClient()`.

#### getModelDetails()

```kotlin
override suspend fun getModelDetails(modelName: String): ShowResponse
```

Calls `POST /api/show` with a `ShowRequest` body and returns detailed model information. Called by `SessionPanel.refreshModelDetails()` when a model is selected.

### Data Models

```kotlin
@Serializable
data class Message(
    val role: String,        // "system", "user", "assistant"
    val content: String,
    val images: List<String>? = null  // base64 for vision
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false,
    val options: Map<String, String>? = null
)

@Serializable
data class ChatResponse(
    val model: String,
    val message: Message,
    val done: Boolean,
    val totalDuration: Long? = null,
    val promptEvalCount: Int? = null,
    val evalCount: Int? = null
)

@Serializable
data class ShowRequest(
    val model: String
)

@Serializable
data class ShowResponse(
    val model: String,
    val modifiedAt: String,
    val parameters: String?,
    val templates: Map<String, String>?,
    val system: String?,
    val license: String?,
    val details: ModelDetails?
)

@Serializable
data class ModelDetails(
    val parentModel: String?,
    val format: String?,
    val family: String?,
    val families: List<String>?,
    val parameterSize: String?,
    val quantizationLevel: String?
)
```

## Provider Interface

```kotlin
interface LLMProvider {
    suspend fun chat(messages: List<Message>): ChatResponse
    suspend fun chat(request: ChatRequestContext): ChatResponse
    suspend fun stream(messages: List<Message>): Flow<ChatResponse>
    suspend fun stream(request: ChatRequestContext): Flow<ChatResponse>
    suspend fun vision(image: ByteArray, prompt: String): ChatResponse
    suspend fun embeddings(text: String): List<Double>
    fun isConnected(): Boolean
    suspend fun checkConnection(): Boolean
    suspend fun getModels(): List<String>
    suspend fun getModelDetails(modelName: String): ShowResponse
}
```

### Future Providers (Not Yet Implemented)

| Provider | Class | Status |
|----------|-------|--------|
| Ollama Cloud | `OllamaCloudProvider` | Planned |
| OpenAI | `OpenAIProvider` | Planned |
| Anthropic | `AnthropicProvider` | Planned |

## Tool Calling

Tool calling is implemented through Spring AI `FunctionCallback` and request-scoped `ChatRequestContext`.

```kotlin
data class ChatRequestContext(
    val messages: List<Message>,
    val model: String? = null,
    val enabledTools: Set<ToolId> = emptySet(),
    val metadata: Map<String, Any> = emptyMap()
)
```

`ToolRegistry` maps application tool IDs to provider-safe function names. IDs such as `file:read` are exposed to Spring AI as names such as `file_read`.

Implemented tool IDs:

| Tool ID | Status |
|---------|--------|
| `ui` | Reads and updates safe UI/session settings |
| `file:read`, `file:list`, `file:glob`, `file:grep` | Workspace-bounded read-only file tools |
| `file:write`, `file:edit` | Workspace-bounded write tools |
| `terminal` | Bounded non-interactive shell execution in the workspace |
| `context`, `pwd` | Runtime context and workspace path |
| `browser`, `search` | Defined tools that return structured unavailable results until a backend is wired |

## Error Handling (Not Yet Implemented)

The current implementation has no structured error handling for LLM calls. Planned:

```kotlin
sealed class LLMError : Exception() {
    class ConnectionError(message: String) : LLMError()
    class TimeoutError(message: String) : LLMError()
    class RateLimitError(message: String) : LLMError()
    class ModelNotFoundError(message: String) : LLMError()
    class AuthenticationError(message: String) : LLMError()
}
```
