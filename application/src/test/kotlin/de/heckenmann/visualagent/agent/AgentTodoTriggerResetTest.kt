package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoTerminalReason
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import reactor.core.publisher.Sinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies that automatic terminal reviews cannot restore cleared conversation history. */
@DatabaseTest
class AgentTodoTriggerResetTest {
    @Test
    fun `late automatic review is rejected and its activity always finishes`() =
        runTest {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
                val provider = mockk<LLMProvider>(relaxed = true)
                val response = Sinks.one<ChatResponse>()
                val subscribed = CompletableDeferred<ChatRequestContext>()
                every { provider.chatReactive(any<ChatRequestContext>()) } answers {
                    val request = firstArg<ChatRequestContext>()
                    response.asMono().doOnSubscribe { subscribed.complete(request) }
                }
                val events = ToolEventBus()
                val finished = CompletableDeferred<Unit>()
                val subscription =
                    events.addListener { event ->
                        if (event.context["trigger"] == "todoChange" && event.phase == ToolCallPhase.FINISHED) finished.complete(Unit)
                    }
                val manager = AgentManager(db, provider, AgentToolConfigService(db), events, TodoEventBus(), AppConfigBean())
                try {
                    val trigger =
                        AgentTodoTrigger(
                            backgroundScope,
                            manager.conversationOps,
                            provider,
                            manager.responseCoordinator,
                            events,
                            mockk<LifecyclePort> { every { closing } returns false },
                            manager.conversationCompletionEvents,
                        )
                    trigger.trigger(Todo("todo-1", "Complete the task", TodoStatus.COMPLETED), TodoTerminalReason.COMPLETED)
                    val request = subscribed.await()
                    val requestId = request.metadata["requestId"] as String
                    assertTrue(requestId.startsWith("todo-trigger-"))
                    assertEquals(requestId, db.getConversationMessages("main").single().conversationRequestId)

                    manager.clearHistory()
                    response.tryEmitValue(ChatResponse("test", Message("assistant", "Late review"), true))
                    finished.await()

                    assertEquals(emptyList(), db.getConversationMessages("main"))
                    assertEquals(emptyList(), manager.getHistory())
                } finally {
                    response.tryEmitEmpty()
                    subscription.close()
                    manager.destroy()
                }
            }
        }
}
