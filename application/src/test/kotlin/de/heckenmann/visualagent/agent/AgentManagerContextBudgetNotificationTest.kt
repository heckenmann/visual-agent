package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerContextBudgetNotificationTest {
    @Test
    fun `context reduction is emitted separately from assistant text`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.streamReactive(any<ChatRequestContext>()) } answers {
                firstArg<ChatRequestContext>().onContextBudgeted?.invoke(
                    ContextBudgetStatus(historyReduced = true, toolSchemasReduced = false),
                )
                Flux.just(ChatResponse(model = "test", message = Message("assistant", "Answer"), done = true))
            }
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val updates = mutableListOf<ConversationStreamUpdate>()

            manager.streamMessage(
                "Follow up",
                onChunk = updates::add,
                userEntryId = "11111111-1111-4111-8111-111111111111",
                assistantEntryId = "22222222-2222-4222-8222-222222222222",
            )

            assertTrue(updates.any { it.contextReduced && it.textDelta.isEmpty() })
            assertEquals("Answer", updates.filterNot { it.contextReduced }.joinToString("") { it.textDelta })
            db.close()
        }
}
