package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Sinks
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies that old provider work cannot restore a conversation after its reset. */
@DatabaseTest
class AgentManagerResetConcurrencyTest {
    @Test
    fun `interrupted request recovery cannot restore history after reset`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            val subscribed = CompletableDeferred<Unit>()
            val connection = Sinks.one<Boolean>()
            every { provider.checkConnectionReactive() } returns connection.asMono().doOnSubscribe { subscribed.complete(Unit) }
            val parent = SupervisorJob()
            val manager =
                AgentManager(
                    db,
                    provider,
                    AgentToolConfigService(db),
                    ToolEventBus(),
                    TodoEventBus(),
                    AppConfigBean(db),
                    scope = CoroutineScope(parent + Dispatchers.IO),
                )
            try {
                val existingJobs = parent.children.toSet()
                manager.pendingResumeMessage = "Interrupted user request"
                manager.conversationOps.resumeInterruptedConversationIfNeeded()
                subscribed.await()
                val recovery = parent.children.single { it !in existingJobs }
                manager.clearHistory()
                connection.tryEmitValue(false)
                recovery.join()

                assertTrue(recovery.isCancelled, "Reset must invalidate the delayed recovery result")
                assertEquals(null, manager.pendingResumeMessage)
                assertEquals(emptyList(), manager.getHistory())
                assertEquals(emptyList(), db.getConversationMessages("main", 100))
            } finally {
                manager.destroy()
                parent.join()
                db.close()
            }
        }

    @Test
    fun `reset racing with committed message projection leaves history empty`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val store = spyk(db)
            val manager =
                AgentManager(store, mockk(relaxed = true), AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val recordRead = CompletableDeferred<Unit>()
            val releaseRead = CompletableFuture<Unit>()
            every { store.getConversationMessage(any()) } answers {
                val record = db.getConversationMessage(firstArg())
                recordRead.complete(Unit)
                releaseRead.get(10, TimeUnit.SECONDS)
                record
            }
            val writer = async(Dispatchers.IO) { manager.appendSystemMessage("Committed before reset") }
            try {
                recordRead.await()
                val resetStarted = CompletableDeferred<Unit>()
                val reset =
                    async(Dispatchers.IO) {
                        resetStarted.complete(Unit)
                        manager.clearHistory()
                    }
                resetStarted.await()
                releaseRead.complete(Unit)
                writer.await()
                reset.await()
                assertEquals(emptyList(), manager.getHistory())
                assertEquals(emptyList(), db.getConversationMessages("main", 100))
            } finally {
                releaseRead.complete(Unit)
                writer.cancel()
                writer.join()
                manager.destroy()
                db.close()
            }
        }

    @Test
    fun `cancelled stream finishing after reset cannot restore deleted messages`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            val subscribed = CompletableDeferred<Unit>()
            val responses = Sinks.many().unicast().onBackpressureBuffer<ChatResponse>()
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                responses.asFlux().doOnSubscribe { subscribed.complete(Unit) }
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val token = CancellationToken()
            val request =
                async(Dispatchers.IO) {
                    runCatching {
                        manager.streamMessage(
                            "Old request",
                            token = token,
                            onChunk = {},
                            userEntryId = "11111111-1111-4111-8111-111111111111",
                            assistantEntryId = "22222222-2222-4222-8222-222222222222",
                        )
                    }
                }
            try {
                subscribed.await()
                token.cancel()
                manager.clearHistory()
                assertEquals(emptyList(), manager.getHistory())

                responses.tryEmitNext(ChatResponse(model = "test", message = Message("assistant", "Late answer"), done = true))
                responses.tryEmitComplete()
                request.await()

                assertEquals(emptyList(), manager.getHistory())
                assertEquals(emptyList(), db.conversationStore.getConversationMessages("main", 100))
            } finally {
                request.cancel()
                request.join()
                manager.destroy()
                db.close()
            }
        }
}
