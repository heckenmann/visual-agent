package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Gates the asynchronous cleanup read before a newer execution takes ownership. */
class AutonomousTodoCleanupOwnershipTest {
    @Test
    fun `stale cleanup cannot release a worker claimed while its snapshot read was pending`() =
        runBlocking {
            withTimeout(10000) {
                val backing = FakeTodoStore()
                val previous = Todo("todo", "Previous task", status = TodoStatus.IN_PROGRESS, assignedAgentId = "worker")
                backing.saveTodo(previous)
                val entered = CompletableDeferred<Unit>()
                val release = Sinks.empty<Void>()
                val store =
                    object : TodoStore by backing {
                        override fun listTodosReactive(): Flux<Todo> =
                            Flux.defer {
                                val captured = backing.listTodos().map { it.copy() }
                                entered.complete(Unit)
                                release.asMono().thenMany(Flux.fromIterable(captured))
                            }
                    }
                val manager = TodoManager(store, TodoEventBus())
                val agent = SubAgent("worker", "Worker", "Implementation", AgentStatus.BUSY, "Previous task", "todo")
                val ops =
                    SubAgentOpsProvider().apply {
                        putSubAgent(agent)
                        setSaveSubAgent { }
                        setNotifyAgent { _, _ -> }
                    }
                val conversation = ConversationOpsProvider(ToolEventBus()).apply { setPersistMessage { it } }
                val oldToken = CancellationToken()
                val newToken = CancellationToken()
                val tokens = ConcurrentHashMap<String, CancellationToken>().apply { put("todo", oldToken) }
                val cleanup =
                    async {
                        cleanupTodoExecution(
                            agent,
                            "todo",
                            "request",
                            oldToken,
                            true,
                            { true },
                            AutoCloseable { },
                            AutoCloseable { },
                            { },
                            tokens,
                            ConcurrentHashMap(),
                            ConcurrentHashMap(),
                            manager,
                            conversation,
                            ops,
                            { },
                        ).awaitSingleOrNull()
                    }
                entered.await()
                tokens["todo"] = newToken
                agent.currentTask = "New task"
                backing.saveTodo(previous.copy(description = "New task", timelineSequence = 2))
                release.tryEmitEmpty()
                cleanup.await()
                assertSame(newToken, tokens["todo"])
                assertEquals(AgentStatus.BUSY, agent.status)
                assertEquals("todo", agent.currentTodoId)
                assertEquals("New task", agent.currentTask)
            }
        }
}
