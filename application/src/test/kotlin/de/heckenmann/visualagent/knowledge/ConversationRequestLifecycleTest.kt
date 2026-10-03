package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Sinks
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Verifies durable request invalidation independently of provider cancellation behavior. */
@DatabaseTest
class ConversationRequestLifecycleTest {
    @Test
    fun `concurrent writer cannot cross a reset transaction`() =
        runBlocking {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
                val request = UUID.randomUUID().toString()
                db.beginConversationRequest("main", request)
                save(db.conversationStore, "main", request)
                val resetLocked = CompletableDeferred<Unit>()
                val releaseReset = Sinks.empty<Void>()
                val reset =
                    async(Dispatchers.IO) {
                        db.transactionalOperator
                            .transactional(
                                db.conversationStore.deleteConversationMessagesReactive("main").flatMap { count ->
                                    resetLocked.complete(Unit)
                                    releaseReset.asMono().thenReturn(count)
                                },
                            ).awaitSingle()
                    }
                val writeSubscribed = CompletableDeferred<Unit>()
                var writer: kotlinx.coroutines.Deferred<Result<String>>? = null
                try {
                    resetLocked.await()
                    writer =
                        async(Dispatchers.IO) {
                            runCatching {
                                db.conversationStore
                                    .saveConversationMessageReactive(
                                        UUID.randomUUID().toString(),
                                        "main",
                                        "assistant",
                                        "Concurrent result",
                                        contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                                        conversationRequestId = request,
                                    ).doOnSubscribe { writeSubscribed.complete(Unit) }
                                    .awaitSingle()
                            }
                        }
                    writeSubscribed.await()
                    releaseReset.tryEmitEmpty()
                    assertEquals(1, reset.await())
                    assertTrue(writer.await().exceptionOrNull() is CancellationException)
                    assertEquals(emptyList(), db.getConversationMessages("main"))
                } finally {
                    releaseReset.tryEmitEmpty()
                    reset.cancel()
                    writer?.cancel()
                    reset.join()
                    writer?.join()
                }
            }
        }

    @Test
    fun `reset rejects work registered before its first message and permits new requests`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
            val oldRequest = UUID.randomUUID().toString()
            db.beginConversationRequest("main", oldRequest)
            db.deleteConversationMessages("main")

            assertFailsWith<CancellationException> { save(db.conversationStore, "main", oldRequest) }
            assertFailsWith<CancellationException> { db.beginConversationRequest("main", oldRequest) }
            assertEquals(emptyList(), db.getConversationMessages("main"))

            val newRequest = UUID.randomUUID().toString()
            db.beginConversationRequest("main", newRequest)
            save(db.conversationStore, "main", newRequest)
            assertEquals(newRequest, db.getConversationMessages("main").single().conversationRequestId)
        }
    }

    @Test
    fun `reset invalidates only the selected session`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
            val request = UUID.randomUUID().toString()
            db.beginConversationRequest("other", request)
            db.deleteConversationMessages("main")

            save(db.conversationStore, "other", request)

            assertEquals(1, db.getConversationMessages("other").size)
            assertEquals(emptyList(), db.getConversationMessages("main"))
        }
    }

    private fun save(
        store: ConversationStore,
        sessionId: String,
        requestId: String,
    ) = store.saveConversationMessage(
        UUID.randomUUID().toString(),
        sessionId,
        "assistant",
        "Late response",
        contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
        conversationRequestId = requestId,
    )
}
