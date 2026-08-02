package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class AgentManagerTodoCountQueryTest {
    @Test
    fun `sendMessage uses model path for todo count question`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.isConnected() } returns true
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse(
                    model = "test",
                    message = Message("assistant", "model-count-response"),
                    done = true,
                )
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.todoManager.add("Task A")
            manager.todoManager.add("Task B")
            manager.todoManager.add("Task C")

            val response = manager.sendMessage("Wie viele todos gibt es aktuell?")

            assertTrue(response.contains("model-count-response"), "Expected model response, got: $response")
            coVerify(exactly = 1) { provider.chat(any<ChatRequestContext>()) }
        }

    @Test
    fun `sendMessage does not hijack explicit todo list request`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.isConnected() } returns true
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse(
                    model = "test",
                    message = Message("assistant", "list-response"),
                    done = true,
                )
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.todoManager.add("Task A")
            val response = manager.sendMessage("show all todos")

            assertTrue(response.contains("list-response"), "Expected LLM path for list intent, got: $response")
            coVerify(exactly = 1) { provider.chat(any<ChatRequestContext>()) }
        }
}
