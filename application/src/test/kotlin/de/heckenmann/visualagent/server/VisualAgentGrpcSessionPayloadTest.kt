package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ProtocolVersion
import de.heckenmann.visualagent.protocol.v1.ChatRequest
import de.heckenmann.visualagent.protocol.v1.ClientFrame
import de.heckenmann.visualagent.protocol.v1.Hello
import de.heckenmann.visualagent.protocol.v1.ServerFrame
import io.grpc.stub.StreamObserver
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Verifies request correlation and validation for user-submitted chat payloads. */
class VisualAgentGrpcSessionPayloadTest {
    @Test
    fun `frames from another session are rejected after handshake`() {
        val conversationPort = mockk<ConversationPort>(relaxed = true)
        val observer = RecordingObserver<ServerFrame>()
        val requestObserver = VisualAgentGrpcSessionService(conversationPort).openSession(observer)
        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId(SESSION_ID)
                .setHello(Hello.newBuilder().setProtocolVersion(ProtocolVersion.CURRENT).build())
                .build(),
        )

        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId("other-session")
                .setRequestId(REQUEST_ID)
                .setChatRequest(
                    ChatRequest
                        .newBuilder()
                        .setContent("hello")
                        .setUserEntryId(USER_ID)
                        .setAssistantEntryId(REQUEST_ID)
                        .build(),
                ).build(),
        )

        assertEquals(
            "SESSION_MISMATCH",
            observer.values
                .last()
                .error.code,
        )
        coVerify(exactly = 0) { conversationPort.stream(any(), any(), any()) }
    }

    @Test
    fun `blank submitted text is rejected without starting the conversation`() {
        val conversationPort = mockk<ConversationPort>(relaxed = true)
        val observer = RecordingObserver<ServerFrame>()
        val requestObserver = VisualAgentGrpcSessionService(conversationPort).openSession(observer)
        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId(SESSION_ID)
                .setHello(Hello.newBuilder().setProtocolVersion(ProtocolVersion.CURRENT).build())
                .build(),
        )
        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId(SESSION_ID)
                .setRequestId(REQUEST_ID)
                .setChatRequest(
                    ChatRequest
                        .newBuilder()
                        .setContent(" ")
                        .setUserEntryId(USER_ID)
                        .setAssistantEntryId(REQUEST_ID)
                        .build(),
                ).build(),
        )

        assertEquals(
            "INVALID_ARGUMENT",
            observer.values
                .last()
                .error.code,
        )
        assertFalse(observer.values.any(ServerFrame::hasChatCompleted))
        coVerify(exactly = 0) { conversationPort.stream(any(), any(), any()) }
    }

    private class RecordingObserver<T> : StreamObserver<T> {
        val values = CopyOnWriteArrayList<T>()

        override fun onNext(value: T) {
            values += value
        }

        override fun onError(throwable: Throwable) = Unit

        override fun onCompleted() = Unit
    }

    private companion object {
        const val SESSION_ID = "test-session"
        const val USER_ID = "11111111-1111-4111-8111-111111111111"
        const val REQUEST_ID = "22222222-2222-4222-8222-222222222222"
    }
}
