package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux
import kotlin.test.Test
import kotlin.test.assertEquals

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerLatestUserPriorityTest {
    @Test
    fun `stream message sends the latest user request before the context turn limit is reached`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            val request = slot<ChatRequestContext>()
            every { provider.streamReactive(capture(request)) } returns
                Flux.just(ChatResponse(model = "test", message = Message("assistant", "Answer"), done = true))
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.streamMessage(
                "Create a Markdown table",
                onChunk = {},
                userEntryId = "11111111-1111-4111-8111-111111111111",
                assistantEntryId = "22222222-2222-4222-8222-222222222222",
            )

            assertEquals(
                "Create a Markdown table",
                request.captured.messages
                    .last { it.role == "user" }
                    .content,
            )
        }
}
