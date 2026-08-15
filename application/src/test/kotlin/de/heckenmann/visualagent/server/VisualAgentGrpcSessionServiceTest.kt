package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ProtocolVersion
import de.heckenmann.visualagent.protocol.v1.ChatRequest
import de.heckenmann.visualagent.protocol.v1.ClientFrame
import de.heckenmann.visualagent.protocol.v1.Hello
import de.heckenmann.visualagent.protocol.v1.ServerFrame
import io.grpc.stub.StreamObserver
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
            "v1",
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
        coEvery { conversationPort.stream("hello", any(), any()) } coAnswers {
            thirdArg<(String) -> Unit>().invoke("world")
        }
        val sessionService = VisualAgentGrpcSessionService(conversationPort, scope)
        val observer = RecordingObserver<ServerFrame>()
        val requestObserver = sessionService.openSession(observer)

        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("test-session")
                .setRequestId("request-1")
                .setHello(Hello.newBuilder().setProtocolVersion(ProtocolVersion.CURRENT).build())
                .build(),
        )
        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("test-session")
                .setRequestId("request-1")
                .setChatRequest(ChatRequest.newBuilder().setContent("hello").build())
                .build(),
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

    private class RecordingObserver<T> : StreamObserver<T> {
        val values = mutableListOf<T>()

        override fun onNext(value: T) {
            values += value
        }

        override fun onError(throwable: Throwable) = Unit

        override fun onCompleted() = Unit
    }
}
