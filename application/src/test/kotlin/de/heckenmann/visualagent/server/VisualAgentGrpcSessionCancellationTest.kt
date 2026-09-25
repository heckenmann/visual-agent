package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamResult
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate
import de.heckenmann.visualagent.protocol.ProtocolVersion
import de.heckenmann.visualagent.protocol.v1.CancelRequest
import de.heckenmann.visualagent.protocol.v1.ChatRequest
import de.heckenmann.visualagent.protocol.v1.ClientFrame
import de.heckenmann.visualagent.protocol.v1.Hello
import de.heckenmann.visualagent.protocol.v1.ServerFrame
import io.grpc.stub.StreamObserver
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies gRPC session cancellation and replacement of reactive conversation subscriptions. */
class VisualAgentGrpcSessionCancellationTest {
    @Test
    fun `replacing a streaming request keeps cancellation state scoped to each request`() =
        runBlocking {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondCompleted = CompletableDeferred<Unit>()
            val conversationPort = mockk<ConversationPort>(relaxed = true)
            coEvery { conversationPort.stream(any(), any(), any()) } coAnswers {
                when (firstArg<ConversationStreamRequest>().content) {
                    "first" -> {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    "second" -> {
                        thirdArg<(ConversationStreamUpdate) -> Unit>().invoke(
                            ConversationStreamUpdate(REQUEST_TWO, "second-result"),
                        )
                        secondCompleted.complete(Unit)
                    }
                }
                ConversationStreamResult(ConversationMessage("assistant", "", id = firstArg<ConversationStreamRequest>().assistantEntryId))
            }
            val observer = RecordingObserver<ServerFrame>()
            val requestObserver = VisualAgentGrpcSessionService(conversationPort).openSession(observer)
            requestObserver.onNext(helloFrame())
            requestObserver.onNext(chatFrame(REQUEST_ONE, USER_ONE, "first"))
            firstStarted.await()
            requestObserver.onNext(chatFrame(REQUEST_TWO, USER_TWO, "second"))
            releaseFirst.complete(Unit)
            secondCompleted.await()
            observer.awaitFrame { it.requestId == REQUEST_TWO && it.hasChatCompleted() }

            val secondFrames = observer.values.filter { it.requestId == REQUEST_TWO }
            assertEquals("second-result", secondFrames.single { it.hasChatDelta() }.chatDelta.text)
            assertEquals(true, secondFrames.any { it.hasChatCompleted() })
            assertEquals(true, observer.values.filter { it.hasError() }.all { it.requestId == REQUEST_ONE })
        }

    @Test
    fun `transport failure cancels the reactive conversation subscription`() =
        runBlocking {
            val streamStarted = CompletableDeferred<Unit>()
            val tokenCancelled = CompletableDeferred<Unit>()
            val coroutineCancelled = CompletableDeferred<Unit>()
            val conversationPort = mockk<ConversationPort>(relaxed = true)
            coEvery { conversationPort.stream(any(), any(), any()) } coAnswers {
                streamStarted.complete(Unit)
                val token = secondArg<CancellationToken>()
                token.onCancelled { tokenCancelled.complete(Unit) }
                try {
                    awaitCancellation()
                } catch (error: CancellationException) {
                    coroutineCancelled.complete(Unit)
                    throw error
                }
            }
            val observer = RecordingObserver<ServerFrame>()
            val requestObserver = VisualAgentGrpcSessionService(conversationPort).openSession(observer)
            requestObserver.onNext(helloFrame())
            requestObserver.onNext(chatFrame(REQUEST_ONE, USER_ONE, "hello"))
            streamStarted.await()

            requestObserver.onError(IllegalStateException("client disconnected"))

            tokenCancelled.await()
            coroutineCancelled.await()
        }

    @Test
    fun `explicit cancellation sends one terminal cancellation frame`() =
        runBlocking {
            val tokenCancelled = CompletableDeferred<Unit>()
            val conversationPort = mockk<ConversationPort>(relaxed = true)
            coEvery { conversationPort.stream(any(), any(), any()) } coAnswers {
                secondArg<CancellationToken>().onCancelled { tokenCancelled.complete(Unit) }
                awaitCancellation()
            }
            val observer = RecordingObserver<ServerFrame>()
            val requestObserver = VisualAgentGrpcSessionService(conversationPort).openSession(observer)
            requestObserver.onNext(helloFrame())
            requestObserver.onNext(chatFrame(REQUEST_ONE, USER_ONE, "hello"))
            requestObserver.onNext(
                ClientFrame
                    .newBuilder()
                    .setSessionId("test-session")
                    .setRequestId(REQUEST_ONE)
                    .setCancelRequest(CancelRequest.newBuilder().setReason("user requested").build())
                    .build(),
            )

            tokenCancelled.await()
            assertEquals(1, observer.values.count { it.hasError() && it.requestId == REQUEST_ONE })
            assertEquals(
                "CANCELLED",
                observer.values
                    .single { it.hasError() }
                    .error.code,
            )
        }

    private fun helloFrame(): ClientFrame =
        ClientFrame
            .newBuilder()
            .setSessionId("test-session")
            .setHello(Hello.newBuilder().setProtocolVersion(ProtocolVersion.CURRENT).build())
            .build()

    private fun chatFrame(
        requestId: String,
        userEntryId: String,
        content: String,
    ): ClientFrame =
        ClientFrame
            .newBuilder()
            .setSessionId("test-session")
            .setRequestId(requestId)
            .setChatRequest(
                ChatRequest
                    .newBuilder()
                    .setContent(content)
                    .setUserEntryId(userEntryId)
                    .build(),
            ).build()

    private class RecordingObserver<T> : StreamObserver<T> {
        val values = CopyOnWriteArrayList<T>()
        private val frames = Channel<T>(Channel.UNLIMITED)

        override fun onNext(value: T) {
            values += value
            frames.trySend(value)
        }

        override fun onError(throwable: Throwable) = Unit

        override fun onCompleted() = Unit

        suspend fun awaitFrame(predicate: (T) -> Boolean): T {
            values.firstOrNull(predicate)?.let { return it }
            while (true) {
                frames.receive().takeIf(predicate)?.let { return it }
            }
        }
    }

    private companion object {
        const val REQUEST_ONE = "11111111-1111-4111-8111-111111111111"
        const val REQUEST_TWO = "22222222-2222-4222-8222-222222222222"
        const val USER_ONE = "33333333-3333-4333-8333-333333333333"
        const val USER_TWO = "44444444-4444-4444-8444-444444444444"
    }
}
