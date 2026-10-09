package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.PersistenceStores
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Proves deletion cannot pass a selected worker whose database claim is still pending. */
@DatabaseTest
class TodoAgentClaimDeletionTest {
    @Test
    fun `deletion waits for selected worker claim and cancels that assignment`() =
        runBlocking {
            withTimeout(30000) {
                KnowledgeDbTestFactory.create("jdbc:h2:mem:delete-claim").use { db ->
                    val claimEntered = CompletableDeferred<Unit>()
                    val releaseClaim = CountDownLatch(1)
                    val deleted = CompletableDeferred<Boolean>()
                    val stores =
                        object : PersistenceStores by db {
                            override fun claimPendingTodo(
                                todoId: String,
                                agentId: String,
                            ): Todo? {
                                claimEntered.complete(Unit)
                                check(releaseClaim.await(10, TimeUnit.SECONDS)) { "Claim gate was not released" }
                                return db.claimPendingTodo(todoId, agentId)
                            }
                        }
                    val provider = mockk<LLMProvider>(relaxed = true)
                    every { provider.streamReactive(any<ChatRequestContext>()) } answers {
                        flux<ChatResponse> { awaitCancellation() }
                    }
                    val manager =
                        AgentManager(stores, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
                    var deletion: Thread? = null
                    try {
                        val worker = manager.createAgent("Worker", "Implementation")
                        val todo = manager.todoManager.add("Claimed task")
                        assertTrue(manager.autonomyOps.startTodo(todo.id))
                        claimEntered.await()
                        val deletionThread =
                            thread(name = "delete-selected-worker") {
                                try {
                                    deleted.complete(manager.deleteAgent(worker.id))
                                } catch (error: Throwable) {
                                    deleted.completeExceptionally(error)
                                }
                            }
                        deletion = deletionThread
                        withTimeout(5000) {
                            while (deletionThread.state != Thread.State.BLOCKED) {
                                check(!deleted.isCompleted) { "Deletion passed the pending claim" }
                                delay(1)
                            }
                        }
                        assertTrue(manager.getSubAgent(worker.id) === worker)
                        releaseClaim.countDown()
                        assertTrue(deleted.await())
                        assertNull(manager.getSubAgent(worker.id))
                        assertTrue(db.listAgents().none { it.id == worker.id })
                        assertEquals(TodoStatus.CANCELLED, manager.todoManager.getById(todo.id)?.status)
                    } finally {
                        releaseClaim.countDown()
                        deletion?.join(5000)
                        manager.destroy()
                    }
                }
            }
        }
}
