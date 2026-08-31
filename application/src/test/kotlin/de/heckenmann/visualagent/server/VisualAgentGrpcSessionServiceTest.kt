package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamResult
import de.heckenmann.visualagent.protocol.ProtocolVersion
import de.heckenmann.visualagent.protocol.v1.ChatRequest
import de.heckenmann.visualagent.protocol.v1.ClientFrame
import de.heckenmann.visualagent.protocol.v1.Hello
import de.heckenmann.visualagent.protocol.v1.ServerFrame
import io.grpc.stub.StreamObserver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies protocol negotiation and safe incompatibility handling at the server boundary. */
class VisualAgentGrpcSessionServiceTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val service = VisualAgentGrpcSessionService(mockk<ConversationPort>(relaxed = true), scope)

    @AfterTest
    fun closeScope() {
        scope.cancel()
    }

    @Test
    fun `hello returns acknowledgement and initial snapshot`() {
        val observer = RecordingObserver<ServerFrame>()
        val requestObserver = service.openSession(observer)

        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("test-session")
                .setHello(
                    Hello
                        .newBuilder()
                        .setProtocolVersion(ProtocolVersion.CURRENT)
                        .setClientName("test")
                        .build(),
                ).build(),
        )

        assertEquals(1, observer.values.size)
        assertEquals(
            ProtocolVersion.CURRENT,
            observer.values
                .single()
                .helloAck
                .protocolVersion,
        )
        assertEquals(
            "{\"ready\":true}",
            observer.values
                .single()
                .snapshot
                .json,
        )
    }

    @Test
    fun `unsupported protocol produces a non retryable error`() {
        val observer = RecordingObserver<ServerFrame>()
        val requestObserver = service.openSession(observer)

        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("test-session")
                .setHello(Hello.newBuilder().setProtocolVersion("v999").build())
                .build(),
        )

        assertEquals(
            "INCOMPATIBLE_PROTOCOL",
            observer.values
                .single()
                .error
                .code,
        )
        assertEquals(
            false,
            observer.values
                .single()
                .error
                .retryable,
        )
    }

    @Test
    fun `chat before handshake is rejected without invoking the agent`() {
        val observer = RecordingObserver<ServerFrame>()
        val requestObserver = service.openSession(observer)

        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("test-session")
                .setChatRequest(ChatRequest.newBuilder().setContent("hello").build())
                .build(),
        )

        assertEquals(
            "SESSION_NOT_READY",
            observer.values
                .single()
                .error
                .code,
        )
    }

    @Test
    fun `chat streams deltas through the conversation port and completes`() {
        val conversationPort = mockk<ConversationPort>(relaxed = true)
        coEvery { conversationPort.stream(any(), any(), any()) } coAnswers {
            val request = firstArg<ConversationStreamRequest>()
            assertEquals(USER_ONE, request.userEntryId)
            assertEquals(REQUEST_ONE, request.assistantEntryId)
            thirdArg<(String) -> Unit>().invoke("world")
            ConversationStreamResult(ConversationMessage("assistant", "world", id = request.assistantEntryId))
        }
        val sessionService = VisualAgentGrpcSessionService(conversationPort, scope)
        val observer = RecordingObserver<ServerFrame>()
        val requestObserver = sessionService.openSession(observer)

        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("test-session")
                .setRequestId(REQUEST_ONE)
                .setHello(Hello.newBuilder().setProtocolVersion(ProtocolVersion.CURRENT).build())
                .build(),
        )
        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("test-session")
                .setRequestId(REQUEST_ONE)
                .setChatRequest(
                    ChatRequest
                        .newBuilder()
                        .setContent("hello")
                        .setUserEntryId(USER_ONE)
                        .build(),
                ).build(),
        )

        assertEquals(
            "world",
            observer.values
                .first { it.hasChatDelta() }
                .chatDelta.text,
        )
        assertEquals(
            true,
            observer.values
                .first { it.hasChatCompleted() }
                .chatCompleted.successful,
        )
    }

    @Test
    fun `invalid chat identities are rejected before the conversation port is called`() {
        val conversationPort = mockk<ConversationPort>(relaxed = true)
        val sessionService = VisualAgentGrpcSessionService(conversationPort, scope)
        val observer = RecordingObserver<ServerFrame>()
        val requestObserver = sessionService.openSession(observer)
        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("test-session")
                .setHello(Hello.newBuilder().setProtocolVersion(ProtocolVersion.CURRENT).build())
                .build(),
        )

        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("test-session")
                .setRequestId(REQUEST_ONE)
                .setChatRequest(
                    ChatRequest
                        .newBuilder()
                        .setContent("hello")
                        .setUserEntryId("invalid")
                        .build(),
                ).build(),
        )

        assertEquals(
            "INVALID_ARGUMENT",
            observer.values
                .last()
                .error.code,
        )
        coVerify(exactly = 0) { conversationPort.stream(any(), any(), any()) }
    }

    @Test
    fun `replacing a streaming request keeps cancellation state scoped to each request`() =
        runTest {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondCompleted = CompletableDeferred<Unit>()
            val conversationPort = mockk<ConversationPort>(relaxed = true)
            coEvery { conversationPort.stream(any(), any(), any()) } coAnswers {
                when (firstArg<de.heckenmann.visualagent.protocol.ConversationStreamRequest>().content) {
                    "first" -> {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    "second" -> {
                        thirdArg<(String) -> Unit>().invoke("second-result")
                        secondCompleted.complete(Unit)
                    }
                }
                ConversationStreamResult(
                    ConversationMessage(
                        "assistant",
                        "",
                        id = firstArg<de.heckenmann.visualagent.protocol.ConversationStreamRequest>().assistantEntryId,
                    ),
                )
            }
            val sessionService = VisualAgentGrpcSessionService(conversationPort, scope)
            val observer = RecordingObserver<ServerFrame>()
            val requestObserver = sessionService.openSession(observer)
            requestObserver.onNext(
                ClientFrame
                    .newBuilder()
                    .setSessionId("test-session")
                    .setHello(Hello.newBuilder().setProtocolVersion(ProtocolVersion.CURRENT).build())
                    .build(),
            )
            requestObserver.onNext(
                ClientFrame
                    .newBuilder()
                    .setSessionId("test-session")
                    .setRequestId(REQUEST_ONE)
                    .setChatRequest(
                        ChatRequest
                            .newBuilder()
                            .setContent("first")
                            .setUserEntryId(USER_ONE)
                            .build(),
                    ).build(),
            )
            withTimeout(1_000) { firstStarted.await() }
            requestObserver.onNext(
                ClientFrame
                    .newBuilder()
                    .setSessionId("test-session")
                    .setRequestId(REQUEST_TWO)
                    .setChatRequest(
                        ChatRequest
                            .newBuilder()
                            .setContent("second")
                            .setUserEntryId(USER_TWO)
                            .build(),
                    ).build(),
            )
            releaseFirst.complete(Unit)
            withTimeout(1_000) { secondCompleted.await() }

            val secondFrames = observer.values.filter { it.requestId == REQUEST_TWO }
            assertEquals("second-result", secondFrames.single { it.hasChatDelta() }.chatDelta.text)
            assertEquals(true, secondFrames.any { it.hasChatCompleted() })
            assertEquals(true, observer.values.filter { it.hasError() }.all { it.requestId == REQUEST_ONE })
        }

    private class RecordingObserver<T> : StreamObserver<T> {
        val values = mutableListOf<T>()

        override fun onNext(value: T) {
            values += value
        }

        override fun onError(throwable: Throwable) = Unit

        override fun onCompleted() = Unit
    }

    private companion object {
        const val REQUEST_ONE = "11111111-1111-4111-8111-111111111111"
        const val REQUEST_TWO = "22222222-2222-4222-8222-222222222222"
        const val USER_ONE = "33333333-3333-4333-8333-333333333333"
        const val USER_TWO = "44444444-4444-4444-8444-444444444444"
    }
}
