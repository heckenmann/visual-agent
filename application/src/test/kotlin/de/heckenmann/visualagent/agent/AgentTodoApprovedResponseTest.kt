package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoApproval
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Proves approved review responses reach persisted conversation history without another model call. */
@OptIn(ExperimentalCoroutinesApi::class)
@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentTodoApprovedResponseTest {
    @Test
    fun `worker completion invokes one main review and publishes its feedback through the terminal listener`(): Unit =
        runTest {
            val fixture = createInstantTodoAgentManager()
            val manager = fixture.manager
            val completion = CompletableDeferred<ConversationCompletionEvent>()
            val handle = manager.conversationCompletionEvents.addListener { completion.complete(it) }
            every { fixture.provider.streamReactive(any<ChatRequestContext>()) } returns
                Flux.just(ChatResponse("test", Message("assistant", "Worker output"), true))
            every { fixture.provider.chatReactive(any<ChatRequestContext>()) } returns
                Mono.just(
                    ChatResponse(
                        "test",
                        Message("assistant", """{"verdict":"APPROVED","feedback":"The reviewed output is ready."}"""),
                        true,
                    ),
                )
            try {
                val worker = manager.createAgent("Worker", "Implementation", "researcher")
                val todo = manager.todoManager.add("Write output", worker.id)
                assertTrue(manager.startTodo(todo.id))
                val event = completion.await()
                assertEquals(TodoStatus.COMPLETED, manager.todoManager.getById(todo.id)?.status)
                val assistant = manager.getHistory().single { it.id == event.assistantEntryId }
                assertEquals("The reviewed output is ready.", assistant.content)
                verify(exactly = 1) { fixture.provider.chatReactive(match<ChatRequestContext> { it.metadata["sessionId"] == "review" }) }
                verify(exactly = 1) { fixture.provider.chatReactive(any<ChatRequestContext>()) }
            } finally {
                handle.close()
                manager.destroy()
            }
        }

    @Test
    fun `approved completion publishes the original feedback without invoking the provider`(): Unit =
        runTest {
            val fixture = createInstantTodoAgentManager()
            val manager = fixture.manager
            val completions = mutableListOf<ConversationCompletionEvent>()
            val handle = manager.conversationCompletionEvents.addListener(completions::add)
            try {
                val trigger = trigger(manager, fixture.provider)
                val requestId = manager.conversationOps.beginConversationRequest()
                trigger.publishApprovedResult(
                    Todo("todo", "Write output", TodoStatus.COMPLETED),
                    TodoApproval("The output is ready.", requestId),
                )
                runCurrent()
                val assistant = manager.getHistory().single { it.role == "assistant" }
                assertEquals("The output is ready.", assistant.content)
                assertEquals(assistant.id, completions.single().assistantEntryId)
                verify(exactly = 0) { fixture.provider.chatReactive(any<ChatRequestContext>()) }
            } finally {
                handle.close()
                manager.destroy()
            }
        }

    @Test
    fun `reset invalidates approved feedback before its first write`(): Unit =
        runTest {
            val fixture = createInstantTodoAgentManager()
            val manager = fixture.manager
            val completions = mutableListOf<ConversationCompletionEvent>()
            val handle = manager.conversationCompletionEvents.addListener(completions::add)
            try {
                val requestId = manager.conversationOps.beginConversationRequest()
                trigger(
                    manager,
                    fixture.provider,
                ).publishApprovedResult(Todo("todo", "Write output", TodoStatus.COMPLETED), TodoApproval("Late feedback", requestId))
                manager.clearHistory()
                runCurrent()
                assertTrue(manager.getHistory().isEmpty())
                assertTrue(completions.isEmpty())
            } finally {
                handle.close()
                manager.destroy()
            }
        }

    @Test
    fun `reset before publishing approval does not register a fresh conversation request`(): Unit =
        runTest {
            val fixture = createInstantTodoAgentManager()
            val manager = fixture.manager
            val requestId = manager.conversationOps.beginConversationRequest()
            try {
                manager.clearHistory()
                trigger(manager, fixture.provider).publishApprovedResult(
                    Todo("todo", "Write output", TodoStatus.COMPLETED),
                    TodoApproval("Late feedback", requestId),
                )
                runCurrent()
                assertTrue(manager.getHistory().isEmpty())
            } finally {
                manager.destroy()
            }
        }

    private fun kotlinx.coroutines.test.TestScope.trigger(
        manager: AgentManager,
        provider: LLMProvider,
    ): AgentTodoTrigger =
        AgentTodoTrigger(
            backgroundScope,
            manager.conversationOps,
            provider,
            manager.responseCoordinator,
            manager.toolEventBus,
            mockk<LifecyclePort> { every { closing } returns false },
            manager.conversationCompletionEvents,
        )
}
