# API Reference

## Ollama Integration

### REST API Endpoints

| Endpoint | Method | Description | Implementation Status |
|----------|--------|-------------|-----------------------|
| `/api/chat` | POST | Chat conversation | Implemented |
| `/api/embeddings` | POST | Generate embeddings | Implemented (has bug) |
| `/api/tags` | GET | Available models | Implemented (used by `checkConnection()`) |

### Implemented Client: OllamaClient

Located at `de.heckenmann.visualagent.agent.OllamaClient`.

```kotlin
class OllamaClient(
    private val baseUrl: String = AppConfig.instance.ollamaLocalUrl,
) : LLMProvider
```

#### chat()

```kotlin
override suspend fun chat(messages: List<Message>): ChatResponse
```

Sends a full chat request and returns the complete response. Uses `ChatRequest.serializer()` for JSON encoding.

#### stream()

```kotlin
override suspend fun stream(messages: List<Message>): Flow<ChatResponse>
```

Streams a chat response in real-time chunks. Parses NDJSON response line by line.

#### vision()

```kotlin
override suspend fun vision(image: ByteArray, prompt: String): ChatResponse
```

**Known Issue**: Hardcodes model `"llava"` instead of using `AppConfig.instance.ollamaModel`.

#### embeddings()

```kotlin
override suspend fun embeddings(text: String): List<Double>
```

**Known Issue**: Uses raw string interpolation `"""{"model":"...","prompt":"$text"}"""` instead of the serializer. No JSON escaping — if `$text` contains quotes, the request will fail.

#### checkConnection()

```kotlin
suspend fun checkConnection(): Boolean
```

Calls `GET /api/tags` to verify Ollama is reachable. Sets the `connected` flag. Currently never called by the UI.

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
```

## Provider Interface

```kotlin
interface LLMProvider {
    suspend fun chat(messages: List<Message>): ChatResponse
    suspend fun stream(messages: List<Message>): Flow<ChatResponse>
    suspend fun vision(image: ByteArray, prompt: String): ChatResponse
    suspend fun embeddings(text: String): List<Double>
    fun isConnected(): Boolean
}
```

### Future Providers (Not Yet Implemented)

| Provider | Class | Status |
|----------|-------|--------|
| Ollama Cloud | `OllamaCloudProvider` | Planned |
| OpenAI | `OpenAIProvider` | Planned |
| Anthropic | `AnthropicProvider` | Planned |

## Tool Calling (Not Yet Implemented)

Tool calling is designed but not yet built. The architecture expects:

```kotlin
data class Tool(
    val name: String,
    val description: String,
    val parameters: JsonSchema
)
```

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