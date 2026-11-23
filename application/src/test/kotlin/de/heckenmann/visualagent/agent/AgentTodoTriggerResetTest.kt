package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.AUTOMATIC_TERMINAL_REVIEW_CONTEXT_KEY
import de.heckenmann.visualagent.agent.tools.SpringAiToolCallbacksAdapter
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
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
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies how automatic terminal reviews interact with persisted conversation history. */
@DatabaseTest
class AgentTodoTriggerResetTest {
    @Test
    fun `automatic todo review persists its first tool turn and final response`() =
        runTest {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
                val provider = mockk<LLMProvider>(relaxed = true)
                val events = ToolEventBus()
                val manager = AgentManager(db, provider, AgentToolConfigService(db), events, TodoEventBus(), AppConfigBean())
                val callbacks = SpringAiToolCallbacksAdapter(ToolRegistry(emptyList(), events), { manager })
                every { provider.chatReactive(any<ChatRequestContext>()) } answers {
                    val request = firstArg<ChatRequestContext>()
                    Mono.fromCallable {
                        callbacks.recordAssistantToolTurn(
                            ProviderTurnResponse(
                                model = "test",
                                content = "",
                                toolCalls = listOf(ProviderToolCall("call-1", "function", "todos", """{"action":"list"}""")),
                                metadata = ProviderResponseMetadata(round = 0),
                            ),
                            request.metadata,
                        )
                        ChatResponse("test", Message("assistant", "Review completed."), true)
                    }
                }
                val finished = CompletableDeferred<Unit>()
                val subscription =
                    events.addListener { event ->
                        if (event.context["trigger"] == "todoChange" && event.phase == ToolCallPhase.FINISHED) finished.complete(Unit)
                    }
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
                    finished.await()

                    val history = db.getConversationMessages("main")
                    assertEquals(3, history.size)
                    val toolTurn = history.single { it.assistantToolTurn }
                    assertEquals("assistant", toolTurn.role)
                    assertEquals(toolTurn.conversationRequestId, toolTurn.id)
                    assertEquals(toolTurn.id, UUID.fromString(toolTurn.id).toString())
                    val review = history.single { it.role == "assistant" && !it.assistantToolTurn }
                    assertEquals("Review completed.", review.content)
                    assertEquals(toolTurn.conversationRequestId, review.conversationRequestId)
                } finally {
                    subscription.close()
                    manager.destroy()
                }
            }
        }

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
                    assertEquals(requestId, UUID.fromString(requestId).toString())
                    assertEquals(true, request.metadata[AUTOMATIC_TERMINAL_REVIEW_CONTEXT_KEY])
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
