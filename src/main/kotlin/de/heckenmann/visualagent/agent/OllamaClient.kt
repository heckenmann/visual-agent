package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OllamaClient(
    private val baseUrl: String = AppConfig.instance.ollamaLocalUrl,
) : LLMProvider {

    private val client = HttpClient(CIO)

    private var connected = false

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    override suspend fun chat(messages: List<Message>): ChatResponse {
        val request = ChatRequest(
            model = AppConfig.instance.ollamaModel,
            messages = messages,
            stream = false,
        )

        val response = client.post("$baseUrl/api/chat") {
            setBody(json.encodeToString(ChatRequest.serializer(), request))
        }

        return json.decodeFromString(response.bodyAsText())
    }

    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = flow {
        val request = ChatRequest(
            model = AppConfig.instance.ollamaModel,
            messages = messages,
            stream = true,
        )

        val response = client.post("$baseUrl/api/chat") {
            setBody(json.encodeToString(ChatRequest.serializer(), request))
        }

        response.bodyAsText().lines().forEach { line ->
            if (line.isNotBlank()) {
                try {
                    val chatResponse: ChatResponse = json.decodeFromString(line)
                    emit(chatResponse)
                } catch (e: Exception) {
                    // Ignore parsing errors for incomplete JSON lines
                }
            }
        }
    }

    override suspend fun vision(image: ByteArray, prompt: String): ChatResponse {
        val base64Image = image.joinToString("") { "%02x".format(it) }
        val message = Message(
            role = "user",
            content = prompt,
            images = listOf(base64Image),
        )

        val request = ChatRequest(
            model = "llava",
            messages = listOf(message),
            stream = false,
        )

        val response = client.post("$baseUrl/api/chat") {
            setBody(json.encodeToString(ChatRequest.serializer(), request))
        }

        return json.decodeFromString(response.bodyAsText())
    }

    override suspend fun embeddings(text: String): List<Double> {
        val requestBody = """{"model":"${AppConfig.instance.ollamaModel}","prompt":"$text"}"""

        val response: String = client.post("$baseUrl/api/embeddings") {
            setBody(requestBody)
        }.bodyAsText()

        return try {
            val jsonResponse = json.parseToJsonElement(response)
            val embeddingArray = jsonResponse.jsonObject["embedding"]?.jsonArray
            embeddingArray?.map { element -> element.jsonPrimitive.content.toDouble() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun isConnected(): Boolean {
        return connected
    }

    suspend fun checkConnection(): Boolean {
        return try {
            val response = client.get("$baseUrl/api/tags")
            connected = response.status.isSuccess()
            connected
        } catch (e: Exception) {
            connected = false
            false
        }
    }
}
