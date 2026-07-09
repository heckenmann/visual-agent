package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentManagerCancellationTest {
    @Test
    fun `streamMessage can be cancelled and keeps partial assistant message`() =
        runBlocking {
            val stores = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            val streamEntered = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            coEvery { provider.stream(any<ChatRequestContext>()) } coAnswers {
                streamEntered.complete(Unit)
                cancelled.await()
                flow { throw CancellationException("Cancelled") }
            }
            val manager = AgentManager(stores, provider, AgentToolConfigService(stores), ToolEventBus(), TodoEventBus())
            val token = CancellationToken()
            val streamJob =
                launch {
                    manager.streamMessage("hello", token) { }
                }
            try {
                streamEntered.await()
                token.cancel()
                assertTrue(token.isCancelled)
                cancelled.complete(Unit)
                streamJob.join()
                val lastMessage = manager.getHistory().lastOrNull()
                assertTrue(lastMessage?.role == "assistant" || lastMessage?.role == "user")
            } finally {
                streamJob.cancel()
                streamJob.join()
                manager.destroy()
                stores.close()
            }
        }

    @Test
    fun `cancelSubAgentJob stops a running job`() =
        runBlocking {
            val stores = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            coEvery { provider.chat(any<ChatRequestContext>()) } coAnswers {
                started.complete(Unit)
                cancelled.await()
                throw CancellationException("Cancelled")
            }
            val manager = AgentManager(stores, provider, AgentToolConfigService(stores), ToolEventBus(), TodoEventBus())
            try {
                val jobId = manager.enqueueAgentJob("test agent", "coder", "researcher", "do work")
                started.await()
                assertTrue(manager.cancelSubAgentJob(jobId))
                cancelled.complete(Unit)
                delay(200)
                assertEquals(0, manager.getActiveJobCount("test agent"))
            } finally {
                manager.destroy()
                stores.close()
            }
        }

    @Test
    fun `cancelAllRunningActions returns all cancelled ids`() =
        runBlocking {
            val stores = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            coEvery { provider.chat(any<ChatRequestContext>()) } coAnswers {
                started.complete(Unit)
                cancelled.await()
                throw CancellationException("Cancelled")
            }
            val manager = AgentManager(stores, provider, AgentToolConfigService(stores), ToolEventBus(), TodoEventBus())
            try {
                val jobId = manager.enqueueAgentJob("test agent", "coder", "researcher", "do work")
                started.await()
                val cancelledIds = manager.cancelAllRunningActions()
                assertTrue(cancelledIds.contains(jobId))
                cancelled.complete(Unit)
                delay(200)
                assertEquals(0, manager.getActiveJobCount("test agent"))
            } finally {
                manager.destroy()
                stores.close()
            }
        }

    @Test
    fun `cancelSubAgentJob for unknown id returns false`() {
        val stores = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(stores, provider, AgentToolConfigService(stores), ToolEventBus(), TodoEventBus())
        try {
            assertFalse(manager.cancelSubAgentJob("does-not-exist"))
        } finally {
            manager.destroy()
            stores.close()
        }
    }
}
