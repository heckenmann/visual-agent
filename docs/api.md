# API Reference

## Ollama Integration

### REST API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/generate` | POST | Text generation |
| `/api/chat` | POST | Chat conversation |
| `/api/embeddings` | POST | Generate embeddings |
| `/api/tags` | GET | Available models |
| `/api/show` | POST | Model details |

### Request/Response Examples

#### Chat Request

```kotlin
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false,
    val options: Options? = null
)

data class Message(
    val role: String, // "system", "user", "assistant"
    val content: String,
    val images: List<String>? = null
)
```

#### Chat Response

```kotlin
data class ChatResponse(
    val model: String,
    val message: Message,
    val done: Boolean,
    val totalDuration: Long? = null,
    val promptEvalCount: Int? = null,
    val evalCount: Int? = null
)
```

### Ktor Client Implementation

```kotlin
class OllamaClient(private val baseUrl: String = "http://localhost:11434") {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
    }
    
    suspend fun chat(request: ChatRequest): ChatResponse {
        return client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    suspend fun streamChat(request: ChatRequest): Flow<ChatResponse> {
        return client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(request.copy(stream = true))
        }.bodyAsFlow()
    }
    
    suspend fun getModels(): List<ModelInfo> {
        return client.get("$baseUrl/api/tags").body()
    }
}
```

## Provider Interface

### LLMProvider

```kotlin
interface LLMProvider {
    /**
     * Send a chat message and receive a response
     */
    suspend fun chat(messages: List<Message>): Response
    
    /**
     * Stream a chat response in real-time
     */
    suspend fun stream(messages: List<Message>): Flow<ResponseChunk>
    
    /**
     * Process images with vision models
     */
    suspend fun vision(image: ByteArray, prompt: String): Response
    
    /**
     * Generate embeddings for text
     */
    suspend fun embeddings(text: String): Embedding
}
```

### Provider Factory

```kotlin
enum class ProviderType {
    OLLAMA_LOCAL,
    OLLAMA_CLOUD,
    OPENAI,
    ANTHROPIC
}

object ProviderFactory {
    fun create(type: ProviderType, config: Config): LLMProvider {
        return when (type) {
            ProviderType.OLLAMA_LOCAL -> OllamaLocalProvider(config.localUrl)
            ProviderType.OLLAMA_CLOUD -> OllamaCloudProvider(config.apiKey)
            ProviderType.OPENAI -> OpenAIProvider(config.apiKey)
            ProviderType.ANTHROPIC -> AnthropicProvider(config.apiKey)
        }
    }
}
```

## Tool Calling

### Tool Definition

```kotlin
data class Tool(
    val name: String,
    val description: String,
    val parameters: JsonSchema
)

data class JsonSchema(
    val type: String,
    val properties: Map<String, Property>,
    val required: List<String>
)
```

### Tool Registry

```kotlin
class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()
    private val handlers = mutableMapOf<String, suspend (Map<String, Any>) -> Any>()
    
    fun register(tool: Tool, handler: suspend (Map<String, Any>) -> Any) {
        tools[tool.name] = tool
        handlers[tool.name] = handler
    }
    
    suspend fun execute(name: String, args: Map<String, Any>): Any {
        val handler = handlers[name] ?: throw ToolNotFoundException(name)
        return handler(args)
    }
    
    fun getToolsJson(): String {
        return Json.encodeToString(tools.values.toList())
    }
}
```

## Rate Limiting

| Provider | Limit | Notes |
|----------|-------|-------|
| Ollama Local | Unlimited | Depends on hardware |
| Ollama Cloud | 100/min (Free) | Upgrade available |
| OpenAI | API-specific | See OpenAI docs |
| Anthropic | API-specific | See Anthropic docs |

## Error Handling

```kotlin
sealed class LLMError : Exception() {
    class ConnectionError(message: String) : LLMError()
    class TimeoutError(message: String) : LLMError()
    class RateLimitError(message: String) : LLMError()
    class ModelNotFoundError(message: String) : LLMError()
    class AuthenticationError(message: String) : LLMError()
}

suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: LLMError) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(LLMError.ConnectionError(e.message ?: "Unknown error"))
    }
}
```

## Example: Complete Integration

```kotlin
class AgentService(private val provider: LLMProvider) {
    private val conversationHistory = mutableListOf<Message>()
    
    suspend fun sendMessage(content: String): String {
        // Add user message
        conversationHistory.add(Message("user", content))
        
        // Get response from provider
        val response = provider.chat(conversationHistory)
        
        // Save assistant response
        conversationHistory.add(response.message)
        
        return response.message.content
    }
    
    suspend fun sendMessageWithTools(content: String): String {
        val tools = ToolRegistry.getToolsJson()
        
        val request = ChatRequest(
            model = "llama3.2",
            messages = conversationHistory + Message("user", content),
            tools = tools
        )
        
        val response = provider.chat(request)
        
        // Process tool call
        if (response.message.toolCall != null) {
            val result = ToolRegistry.execute(
                response.message.toolCall.name,
                response.message.toolCall.arguments
            )
            // Send result back to LLM
            return continueConversation(result)
        }
        
        return response.message.content
    }
}
```
