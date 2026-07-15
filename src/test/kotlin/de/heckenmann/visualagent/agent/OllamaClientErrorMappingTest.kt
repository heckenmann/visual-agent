package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfigBean
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.api.OllamaApi
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OllamaClientErrorMappingTest {
    @Test
    fun `chat surfaces user-facing message without exposing raw http response body`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>()
            val registry = ToolRegistry(emptyList(), ToolEventBus(), AppConfigBean(mockk(relaxed = true)))
            every { ollamaApi.chat(any()) } throws
                ForbiddenLikeException(
                    "403 Forbidden from POST http://localhost:11434/api/chat",
                    "403 Forbidden: this model requires a subscription, upgrade for access",
                )
            val client = createClient(chatModel, ollamaApi, registry)

            val error =
                assertFailsWith<IllegalStateException> {
                    client.chat(listOf(Message("user", "hello")))
                }

            assertTrue(error.message.orEmpty().contains("not available for this account"))
            assertFalse(error.message.orEmpty().contains("403 Forbidden from POST"))
            assertFalse(error.message.orEmpty().contains("this model requires a subscription"))
        }

    @Test
    fun `chat maps 404 to model not available message`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>()
            val registry = ToolRegistry(emptyList(), ToolEventBus(), AppConfigBean(mockk(relaxed = true)))
            every { ollamaApi.chat(any()) } throws
                ForbiddenLikeException(
                    "404 Not Found from POST http://localhost:11434/api/chat",
                    "{\"error\":\"model 'missing' not found\"}",
                )
            val client = createClient(chatModel, ollamaApi, registry)

            val error =
                assertFailsWith<IllegalStateException> {
                    client.chat(listOf(Message("user", "hello")))
                }

            assertTrue(error.message.orEmpty().contains("Model not available"))
            assertTrue(error.message.orEmpty().contains("Pull the model"))
            assertFalse(error.message.orEmpty().contains("404"))
            assertFalse(error.message.orEmpty().contains("missing"))
        }

    class ForbiddenLikeException(
        message: String,
        private val body: String,
    ) : RuntimeException(message) {
        @Suppress("unused")
        fun getResponseBodyAsString(): String = body
    }
}
