package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Mono
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies that provider protocol framing cannot contaminate persisted main-agent history. */
@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerConversationRoleFramingTest {
    @Test
    fun `protocol role framing is removed before assistant messages are persisted and reused`() {
        val tempDb = createTempDirectory("visual-agent-agent-role-framing-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider = mockk<LLMProvider>(relaxed = true)
        val requests = mutableListOf<ChatRequestContext>()
        every { provider.chatReactive(capture(requests)) } returnsMany
            listOf(
                Mono.just(
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", "assistant: First answer"),
                        done = true,
                    ),
                ),
                Mono.just(
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", "Second answer"),
                        done = true,
                    ),
                ),
            )
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

        kotlinx.coroutines.runBlocking {
            manager.sendMessage("First request")
            manager.sendMessage("Second request")
        }

        val persistedAssistant = db.getConversationMessages("main", 20).first { it.role == "assistant" }
        assertEquals("First answer", persistedAssistant.content)
        val reusedAssistant = requests[1].messages.first { it.content == "First answer" }
        assertEquals("First answer", reusedAssistant.content)
        db.close()
    }

    @Test
    fun `legacy assistant role framing is removed when history is reloaded`() {
        val tempDb = createTempDirectory("visual-agent-agent-legacy-role-framing-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        db.saveConversationMessage("main", "user", "Previous request")
        db.saveConversationMessage("main", "assistant", "assistant: Previous answer")
        val provider = mockk<LLMProvider>(relaxed = true)
        val requests = mutableListOf<ChatRequestContext>()
        every { provider.chatReactive(capture(requests)) } returns
            Mono.just(
                ChatResponse(
                    model = "test",
                    message = Message("assistant", "New answer"),
                    done = true,
                ),
            )
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

        assertEquals("Previous answer", manager.getHistory().last().content)
        kotlinx.coroutines.runBlocking {
            manager.sendMessage("New request")
        }

        val reusedAssistant = requests.single().messages.first { it.content == "Previous answer" }
        assertEquals("Previous answer", reusedAssistant.content)
        db.close()
    }
}
