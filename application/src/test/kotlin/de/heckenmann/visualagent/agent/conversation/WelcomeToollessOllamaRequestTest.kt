package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.OllamaClient
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import de.heckenmann.visualagent.agent.ollama.OllamaToolRecovery
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaChatOptions
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.assertTrue

/**
 * End-to-end test for the "clean history" welcome flow.
 *
 * Reproduces the UI action by clearing history through [AgentManager] and then
 * generating the welcome message. The underlying LLM provider is a real
 * [OllamaClient] backed by a mocked [OllamaApi]. The test asserts that the
 * outgoing Ollama HTTP request contains no `tools` array, matching the
 * behaviour that caused HTTP 500 on some endpoints when an empty `tools: []`
 * was sent for tool-less requests.
 */
class WelcomeToollessOllamaRequestTest {
    @Test
    fun `clean history welcome sends Ollama chat request without tools array`() {
        val tempDb =
            createTempDirectory("visual-agent-welcome-toolless-test")
                .resolve("history.db")
                .toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)

        val ollamaApi = mockk<OllamaApi>()
        val requestSlot = slot<OllamaApi.ChatRequest>()
        every { ollamaApi.chat(capture(requestSlot)) } returns
            OllamaApi.ChatResponse(
                "welcome-model",
                Instant.now(),
                OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, "Hello!", null, null, null, null),
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
            )

        val chatModel =
            OllamaChatModel
                .builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder().model("welcome-model").build())
                .build()
        val appConfig = AppConfigBean(db)
        val toolRegistry = ToolRegistry(emptyList(), ToolEventBus(), appConfig)
        val promptFactory = OllamaPromptFactory(toolRegistry)
        val toolRecovery = OllamaToolRecovery(chatModel, promptFactory)
        val ollamaClient = OllamaClient(chatModel, ollamaApi, promptFactory, toolRecovery, toolRegistry, appConfig)

        val stubbedProvider =
            StubbedConnectionOllamaProvider(
                delegate = ollamaClient,
                availableModels = listOf("welcome-model"),
            )
        val previousModel = appConfig.ollamaModel
        appConfig.ollamaModel = "welcome-model"
        try {
            val manager =
                AgentManager(
                    stores = db,
                    llmProvider = stubbedProvider,
                    agentToolConfigService = AgentToolConfigService(db),
                    toolEventBus = ToolEventBus(),
                    todoEventBus = TodoEventBus(),
                    appConfig = appConfig,
                )

            manager.clearHistory()
            runBlocking { manager.addWelcomeMessageAfterReset() }

            val captured = requestSlot.captured
            assertTrue(
                captured.tools().isNullOrEmpty(),
                "Welcome request after clean history must not contain a tools array, but got ${captured.tools()}",
            )
        } finally {
            appConfig.ollamaModel = previousModel
        }

        db.close()
    }

    /**
     * Wraps a real Ollama client so the connection/model checks can be stubbed
     * without stubbing the actual chat implementation.
     */
    private class StubbedConnectionOllamaProvider(
        private val delegate: OllamaClient,
        private val availableModels: List<String>,
    ) : de.heckenmann.visualagent.agent.LLMProvider {
        override suspend fun chat(messages: List<Message>): ChatResponse = delegate.chat(messages)

        override suspend fun chat(request: ChatRequestContext): ChatResponse = delegate.chat(request)

        override suspend fun stream(messages: List<Message>): kotlinx.coroutines.flow.Flow<ChatResponse> = delegate.stream(messages)

        override suspend fun stream(request: ChatRequestContext): kotlinx.coroutines.flow.Flow<ChatResponse> = delegate.stream(request)

        override suspend fun vision(
            image: ByteArray,
            prompt: String,
        ): ChatResponse = delegate.vision(image, prompt)

        override suspend fun getModels(): List<String> = availableModels

        override suspend fun getModelDetails(modelName: String): de.heckenmann.visualagent.agent.ShowResponse =
            delegate.getModelDetails(modelName)

        override suspend fun checkConnection(): Boolean = true

        override fun isConnected(): Boolean = true

        override suspend fun embeddings(text: String): List<Double> = delegate.embeddings(text)
    }
}
