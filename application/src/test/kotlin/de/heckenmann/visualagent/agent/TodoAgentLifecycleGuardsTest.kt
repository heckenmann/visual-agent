package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Integration coverage for deletion and startup recovery with persisted agent identities. */
@DatabaseTest
class TodoAgentLifecycleGuardsTest {
    @Test
    fun `deleting a busy worker cancels its stream and rejects late persistence`() =
        runBlocking {
            withTimeout(30000) {
                KnowledgeDbTestFactory.create("jdbc:h2:mem:delete-worker").use { db ->
                    val started = CompletableDeferred<Unit>()
                    val cancelled = CompletableDeferred<Unit>()
                    val provider = mockk<LLMProvider>(relaxed = true)
                    every { provider.streamReactive(any<ChatRequestContext>()) } answers {
                        flux<ChatResponse> {
                            started.complete(Unit)
                            awaitCancellation()
                        }.doOnCancel { cancelled.complete(Unit) }
                    }
                    every { provider.chatReactive(any<ChatRequestContext>()) } returns
                        Mono.just(ChatResponse("test", Message("assistant", "Work stopped."), true))
                    val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
                    try {
                        val worker = manager.createAgent("Worker", "Implementation")
                        val todo = manager.todoManager.add("Write a short result", worker.id)
                        assertTrue(manager.autonomyOps.startTodo(todo.id))
                        started.await()
                        assertTrue(manager.deleteAgent(worker.id))
                        cancelled.await()
                        manager.saveSubAgent(worker)
                        assertNull(manager.getSubAgent(worker.id))
                        assertTrue(db.listAgents().none { it.id == worker.id })
                        assertEquals(TodoStatus.CANCELLED, manager.todoManager.getById(todo.id)?.status)
                    } finally {
                        manager.destroy()
                    }
                }
            }
        }

    @Test
    fun `startup persists idle reservations and explains orphaned todo cancellation`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:restart-worker").use { db ->
            val provider = mockk<LLMProvider>(relaxed = true)
            val first = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val worker = first.createAgent("Worker", "Implementation")
            first.destroy()
            db.saveAgent(db.listAgents().single().copy(status = "BUSY", currentTask = "Interrupted work"))
            db.saveTodo(Todo("orphan", "Interrupted work", status = TodoStatus.IN_PROGRESS, assignedAgentId = worker.id))
            val restarted = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            try {
                assertEquals("IDLE", db.listAgents().single().status)
                assertNull(db.listAgents().single().currentTask)
                assertEquals(TodoStatus.CANCELLED, db.listTodos().single().status)
                assertTrue(
                    db
                        .listTodos()
                        .single()
                        .terminalDetail!!
                        .contains("restart"),
                )
            } finally {
                restarted.destroy()
            }
        }
    }
}
