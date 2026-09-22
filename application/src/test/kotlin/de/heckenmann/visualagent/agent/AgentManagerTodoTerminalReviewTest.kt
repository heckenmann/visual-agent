package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoTerminalReason
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerTodoTerminalReviewTest {
    @Test
    fun `todo completion persists system message`(): Unit =
        runBlocking {
            val fixture = createInstantTodoAgentManager()
            try {
                val manager = fixture.manager
                val todo = manager.todoManager.add("Trigger test", "1")

                manager.todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)

                val history = manager.getHistory()
                assertTrue(
                    history.any { it.role == "system" && it.content.contains("[COMPLETED]") },
                    "Expected a system message after todo completion, got: ${history.map { it.role to it.content.take(60) }}",
                )
            } finally {
                fixture.manager.destroy()
            }
        }

    @Test
    fun `todo cancellation persists system message`(): Unit =
        runBlocking {
            val fixture = createInstantTodoAgentManager()
            try {
                val manager = fixture.manager
                val todo = manager.todoManager.add("Cancel trigger test", "1")

                manager.todoManager.updateStatus(todo.id, TodoStatus.CANCELLED)

                val history = manager.getHistory()
                assertTrue(
                    history.any { it.role == "system" && it.content.contains("[CANCELLED]") },
                    "Expected a system message after todo cancellation, got: ${history.map { it.role to it.content.take(60) }}",
                )
            } finally {
                fixture.manager.destroy()
            }
        }

    @Test
    fun `completed todo review request ends with an explicit user instruction`(): Unit =
        runBlocking {
            val fixture = createInstantTodoAgentManager()
            try {
                val manager = fixture.manager
                val request = CompletableDeferred<ChatRequestContext>()
                every { fixture.provider.chatReactive(any<ChatRequestContext>()) } answers {
                    mono {
                        request.complete(firstArg<ChatRequestContext>())
                        ChatResponse(model = "test", message = Message("assistant", "Reviewed"), done = true)
                    }
                }
                val todo = manager.todoManager.add("Completed review", "1")

                manager.todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)

                val completedMessage = request.await().messages.last()
                assertEquals("user", completedMessage.role)
                assertEquals(reviewInstruction(todo.id), completedMessage.content)
            } finally {
                fixture.manager.destroy()
            }
        }

    @Test
    fun `failed todo review request includes its terminal outcome`(): Unit =
        runBlocking {
            val fixture = createInstantTodoAgentManager()
            try {
                val manager = fixture.manager
                val request = CompletableDeferred<ChatRequestContext>()
                every { fixture.provider.chatReactive(any<ChatRequestContext>()) } answers {
                    mono {
                        request.complete(firstArg<ChatRequestContext>())
                        ChatResponse(model = "test", message = Message("assistant", "Reviewed"), done = true)
                    }
                }
                val todo = manager.todoManager.add("Cancelled review", "1")

                manager.todoManager.cancelTodo(todo.id, TodoTerminalReason.EXECUTION_FAILED)

                val cancelledMessages = request.await().messages
                assertEquals("user", cancelledMessages.last().role)
                assertEquals(reviewInstruction(todo.id), cancelledMessages.last().content)
                assertTrue(
                    cancelledMessages.any { it.content.contains("EXECUTION_FAILED") },
                    "Expected the main-agent review to receive the failed terminal outcome.",
                )
            } finally {
                fixture.manager.destroy()
            }
        }

    @Test
    fun `terminal todo changes each create one main-agent review notification`() =
        runBlocking {
            val fixture = createInstantTodoAgentManager()
            try {
                val manager = fixture.manager
                val reviewRequests = CompletableDeferred<Int>()
                var requestCount = 0
                every { fixture.provider.chatReactive(any<ChatRequestContext>()) } answers {
                    mono {
                        requestCount += 1
                        if (requestCount == 2) reviewRequests.complete(requestCount)
                        ChatResponse(model = "test", message = Message("assistant", "Reviewed"), done = true)
                    }
                }
                val first = manager.todoManager.add("First terminal todo", "1")
                val second = manager.todoManager.add("Second terminal todo", "2")

                manager.todoManager.updateStatus(first.id, TodoStatus.COMPLETED)
                manager.todoManager.cancelTodo(second.id, TodoTerminalReason.EXECUTION_FAILED)

                reviewRequests.await()
                val terminalReviews = manager.getHistory().filter { it.metadata?.contains("todo_terminal_transition") == true }
                assertTrue(terminalReviews.any { it.metadata?.contains(first.id) == true })
                assertTrue(terminalReviews.any { it.metadata?.contains(second.id) == true })
            } finally {
                fixture.manager.destroy()
            }
        }

    @Test
    fun `non-terminal status change does not persist completion message`(): Unit =
        runBlocking {
            val fixture = createInstantTodoAgentManager()
            try {
                val manager = fixture.manager
                val todo = manager.todoManager.add("No trigger", "1")

                manager.todoManager.updateStatus(todo.id, TodoStatus.IN_PROGRESS)

                assertTrue(
                    manager.getHistory().none { it.role == "system" && it.content.contains("[COMPLETED]") },
                    "Expected no completion system message for non-terminal status change",
                )
            } finally {
                fixture.manager.destroy()
            }
        }

    private fun reviewInstruction(todoId: String): String =
        "Review the todo with id=$todoId described in the preceding system notification " +
            "and carry out its instructions. Do not substitute another todo."
}
