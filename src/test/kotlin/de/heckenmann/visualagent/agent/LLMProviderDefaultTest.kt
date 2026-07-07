package de.heckenmann.visualagent.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LLMProviderDefaultTest {
    @Test
    fun `default chat delegates to message list overload`() =
        runTest {
            val provider =
                object : LLMProvider {
                    override suspend fun chat(messages: List<Message>): ChatResponse =
                        ChatResponse("model", Message("assistant", "ok"), true)

                    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = flowOf()

                    override suspend fun vision(
                        image: ByteArray,
                        prompt: String,
                    ): ChatResponse = throw UnsupportedOperationException()

                    override suspend fun embeddings(text: String): List<Double> = emptyList()

                    override fun isConnected(): Boolean = true

                    override suspend fun checkConnection(): Boolean = true

                    override suspend fun getModels(): List<String> = emptyList()

                    override suspend fun getModelDetails(modelName: String): ShowResponse =
                        ShowResponse(modelName, "", details = ModelDetails(family = "test"))
                }

            val response = provider.chat(ChatRequestContext(messages = listOf(Message("user", "hi"))))

            assertEquals("ok", response.message.content)
        }

    @Test
    fun `default stream delegates to message list overload`() =
        runTest {
            val provider =
                object : LLMProvider {
                    override suspend fun chat(messages: List<Message>): ChatResponse = throw UnsupportedOperationException()

                    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> =
                        flowOf(ChatResponse("m", Message("assistant", "chunk"), true))

                    override suspend fun vision(
                        image: ByteArray,
                        prompt: String,
                    ): ChatResponse = throw UnsupportedOperationException()

                    override suspend fun embeddings(text: String): List<Double> = emptyList()

                    override fun isConnected(): Boolean = true

                    override suspend fun checkConnection(): Boolean = true

                    override suspend fun getModels(): List<String> = emptyList()

                    override suspend fun getModelDetails(modelName: String): ShowResponse =
                        ShowResponse(modelName, "", details = ModelDetails(family = "test"))
                }

            val chunks = provider.stream(ChatRequestContext(messages = listOf(Message("user", "hi")))).toList()

            assertEquals("chunk", chunks.single().message.content)
        }

    @Test
    fun `default getModels with provider id delegates to parameterless overload`() =
        runTest {
            val provider =
                object : LLMProvider {
                    override suspend fun chat(messages: List<Message>): ChatResponse = throw UnsupportedOperationException()

                    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = flowOf()

                    override suspend fun vision(
                        image: ByteArray,
                        prompt: String,
                    ): ChatResponse = throw UnsupportedOperationException()

                    override suspend fun embeddings(text: String): List<Double> = emptyList()

                    override fun isConnected(): Boolean = true

                    override suspend fun checkConnection(): Boolean = true

                    override suspend fun getModels(): List<String> = listOf("model-a")

                    override suspend fun getModelDetails(modelName: String): ShowResponse =
                        ShowResponse(modelName, "", details = ModelDetails(family = "test"))
                }

            assertEquals(listOf("model-a"), provider.getModels("ignored"))
        }

    @Test
    fun `default getModelDetails with provider id delegates to single argument overload`() =
        runTest {
            val provider =
                object : LLMProvider {
                    override suspend fun chat(messages: List<Message>): ChatResponse = throw UnsupportedOperationException()

                    override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = flowOf()

                    override suspend fun vision(
                        image: ByteArray,
                        prompt: String,
                    ): ChatResponse = throw UnsupportedOperationException()

                    override suspend fun embeddings(text: String): List<Double> = emptyList()

                    override fun isConnected(): Boolean = true

                    override suspend fun checkConnection(): Boolean = true

                    override suspend fun getModels(): List<String> = emptyList()

                    override suspend fun getModelDetails(modelName: String): ShowResponse =
                        ShowResponse(modelName, "now", details = ModelDetails(family = "family"))
                }

            assertEquals("now", provider.getModelDetails("any", "target-model").modifiedAt)
        }
}
