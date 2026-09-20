package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerClearHistoryTest {
    private fun createManager(): AgentManager {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:h2:mem:test")
        val provider = mockk<LLMProvider>(relaxed = true)
        every { provider.isConnected() } returns true
        every { provider.getModelsReactive() } returns Mono.just(listOf("test-model"))
        every { provider.chatReactive(any<ChatRequestContext>()) } returns
            Mono.just(
                ChatResponse(
                    model = "test-model",
                    message = Message("assistant", "Welcome back!"),
                    done = true,
                ),
            )
        val config = AppConfigBean(db)
        config.ollamaModel = "test-model"
        return AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), config)
    }

    @Test
    fun `clearHistory then add message then loadOlderHistory produces no duplicates`(): Unit =
        runBlocking {
            val manager = createManager()
            try {
                // Seed some history so loadedHistoryCount > 0
                manager.appendSystemMessage("system context")
                manager.appendSystemMessage("another system message")
                assertEquals(2, manager.getHistory().size)

                // Clear and add a message (simulating addWelcomeMessageAfterReset)
                manager.clearHistory()
                manager.appendSystemMessage("welcome back")
                val afterWelcome = manager.getHistory()
                assertEquals(1, afterWelcome.size, "should have exactly one message after reset")

                // Simulate what ConversationOlderHistoryLoader does
                val loaded = manager.loadOlderHistory()
                val existingIds = afterWelcome.map { it.id }.toSet()
                val newMessages = loaded.filter { it.id !in existingIds }

                assertEquals(
                    0,
                    newMessages.size,
                    "loadOlderHistory should not return messages already in the in-memory list",
                )

                // The in-memory list must not have duplicates
                val finalHistory = manager.getHistory()
                val ids = finalHistory.mapNotNull { it.id }
                assertEquals(ids.size, ids.toSet().size, "history must not contain duplicate IDs")
                assertEquals(1, finalHistory.size, "history should still have exactly one message")
            } finally {
                manager.destroy()
            }
        }

    @Test
    fun `clearTodos removes persisted todos`(): Unit =
        runBlocking {
            val manager = createManager()
            try {
                manager.todoManager.add("Remove me")

                manager.clearTodos()

                assertEquals(emptyList(), manager.getTodosFromDb())
            } finally {
                manager.destroy()
            }
        }
}
