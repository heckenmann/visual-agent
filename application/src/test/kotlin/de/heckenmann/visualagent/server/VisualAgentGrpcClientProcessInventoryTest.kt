package de.heckenmann.visualagent.server

import com.google.protobuf.ByteString
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamResult
import de.heckenmann.visualagent.protocol.ProtocolVersion
import de.heckenmann.visualagent.protocol.v1.ChatRequest
import de.heckenmann.visualagent.protocol.v1.ClientDataResponse
import de.heckenmann.visualagent.protocol.v1.ClientFrame
import de.heckenmann.visualagent.protocol.v1.Hello
import de.heckenmann.visualagent.protocol.v1.ServerFrame
import io.grpc.stub.StreamObserver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies direct user submission and rejection of unsolicited client diagnostics. */
class VisualAgentGrpcClientProcessInventoryTest {
    @Test
    fun `chat text is submitted directly and unsolicited client data is rejected`() {
        val chatText = "inspect the client process list"
        val conversationPort = mockk<ConversationPort>(relaxed = true)
        coEvery { conversationPort.stream(any(), any(), any()) } coAnswers {
            val request = firstArg<ConversationStreamRequest>()
            assertEquals(chatText, request.content)
            assertNull(request.clientDataRequester)
            ConversationStreamResult(ConversationMessage("assistant", "ok", id = request.assistantEntryId))
        }
        val observer = RecordingObserver<ServerFrame>()
        val requestObserver = VisualAgentGrpcSessionService(conversationPort).openSession(observer)
        requestObserver.onNext(helloFrame())
        val intent = chatFrame()
        requestObserver.onNext(intent)
        runBlocking { observer.awaitFrame(ServerFrame::hasChatCompleted) }
        assertEquals(false, observer.values.any(ServerFrame::hasClientDataRequest))

        val response =
            ClientFrame
                .newBuilder()
                .setSessionId(SESSION_ID)
                .setRequestId(REQUEST_ID)
                .setClientDataResponse(
                    ClientDataResponse
                        .newBuilder()
                        .setDataRequestId("unsolicited")
                        .setPayload(ByteString.copyFromUtf8(chatText))
                        .setContentType("text/plain; charset=utf-8")
                        .build(),
                ).build()
        requestObserver.onNext(response)

        coVerify(exactly = 1) { conversationPort.stream(any(), any(), any()) }
        assertEquals(1, observer.values.count { it.hasError() && it.error.code == "UNSOLICITED_DATA" })
    }

    private fun helloFrame() =
        ClientFrame
            .newBuilder()
            .setSessionId(SESSION_ID)
            .setHello(Hello.newBuilder().setProtocolVersion(ProtocolVersion.CURRENT).build())
            .build()

    private fun chatFrame() =
        ClientFrame
            .newBuilder()
            .setSessionId(SESSION_ID)
            .setRequestId(REQUEST_ID)
            .setChatRequest(
                ChatRequest
                    .newBuilder()
                    .setContent("inspect the client process list")
                    .setUserEntryId(USER_ID)
                    .setAssistantEntryId(REQUEST_ID)
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
            while (true) frames.receive().takeIf(predicate)?.let { return it }
        }
    }

    private companion object {
        const val SESSION_ID = "test-session"
        const val REQUEST_ID = "11111111-1111-4111-8111-111111111111"
        const val USER_ID = "33333333-3333-4333-8333-333333333333"
    }
}
