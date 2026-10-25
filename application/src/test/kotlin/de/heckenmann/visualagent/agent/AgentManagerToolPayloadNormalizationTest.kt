package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Mono
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerToolPayloadNormalizationTest {
    @Test
    fun `textless native replay remains available after loading persisted history`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory.create(
                createTempDirectory("visual-agent-empty-replay").resolve("history.db").toString(),
            )
        try {
            val replay = ProviderReplayState("openai.responses.parts.v1", "profile", "model", "native-parts")
            val responseId = UUID.randomUUID().toString()
            db.saveConversationMessage(UUID.randomUUID().toString(), "main", "user", "Inspect the result", null)
            db.saveConversationMessage(
                responseId,
                "main",
                "assistant",
                "",
                null,
                ConversationContextPolicy.DIALOGUE,
                providerReplay = replay,
            )
            val manager =
                AgentManager(db, mockk(relaxed = true), AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            assertEquals(replay, manager.getHistory().single { it.id == responseId }.providerReplay)
            val history = manager.conversationOps.loadMainAgentContextFromDb()
            assertEquals(replay, history.single { it.id == responseId }.providerReplay)
            val request = manager.conversationOps.buildMainRequest(history)
            assertEquals(replay, request.messages.single { it.id == responseId }.providerReplay)
        } finally {
            db.close()
        }
    }

    @Test
    fun `sendMessage does not fabricate an answer for an empty tool_calls payload`() {
        val tempDb = createTempDirectory("visual-agent-tool-payload-test").resolve("history.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider = mockk<LLMProvider>(relaxed = true)
        every { provider.chatReactive(any<ChatRequestContext>()) } returns
            Mono.just(
                ChatResponse(
                    model = "test",
                    message = Message("assistant", """{"tool_calls": []}"""),
                    done = true,
                ),
            )
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

        val response =
            runBlocking {
                manager.sendMessage("Bitte rufe ein Tool auf")
            }

        assertEquals("", response)
        assertEquals("", manager.getHistory().last().content)
        assertEquals("", db.getConversationMessages("main", 10).single { it.role == "assistant" }.content)
        db.close()
    }
}
