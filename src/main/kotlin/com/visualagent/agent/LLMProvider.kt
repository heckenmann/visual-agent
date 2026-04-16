package com.visualagent.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface LLMProvider {
    suspend fun chat(messages: List<Message>): ChatResponse
    suspend fun stream(messages: List<Message>): Flow<ChatResponse>
    suspend fun vision(image: ByteArray, prompt: String): ChatResponse
    suspend fun embeddings(text: String): List<Double>
    fun isConnected(): Boolean
}

@Serializable
data class Message(
    val role: String,
    val content: String,
    val images: List<String>? = null
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
