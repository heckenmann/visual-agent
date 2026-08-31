package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ProtocolVersion
import de.heckenmann.visualagent.protocol.v1.CancelRequest
import de.heckenmann.visualagent.protocol.v1.ChatCompleted
import de.heckenmann.visualagent.protocol.v1.ChatDelta
import de.heckenmann.visualagent.protocol.v1.ClientFrame
import de.heckenmann.visualagent.protocol.v1.HelloAck
import de.heckenmann.visualagent.protocol.v1.OperationError
import de.heckenmann.visualagent.protocol.v1.ServerFrame
import de.heckenmann.visualagent.protocol.v1.Snapshot
import de.heckenmann.visualagent.protocol.v1.VisualAgentSessionServiceGrpc
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

/** Bridges one bidirectional protocol session to the application services. */
@Component
class VisualAgentGrpcSessionService(
    private val conversationPort: ConversationPort,
    private val scope: CoroutineScope,
) : VisualAgentSessionServiceGrpc.VisualAgentSessionServiceImplBase() {
    override fun openSession(responseObserver: StreamObserver<ServerFrame>): StreamObserver<ClientFrame> {
        val session = Session(responseObserver)
        return object : StreamObserver<ClientFrame> {
            override fun onNext(frame: ClientFrame) = session.accept(frame)

            override fun onError(throwable: Throwable) = session.close(throwable)

            override fun onCompleted() = session.complete()
        }
    }

    private inner class Session(
        private val responseObserver: StreamObserver<ServerFrame>,
    ) {
        private var sessionId = ""
        private var revision = 0L

        @Volatile private var activeRequest: RequestState? = null
        private var helloReceived = false

        /** Handles one client frame without exposing application services to the transport. */
        fun accept(frame: ClientFrame) {
            sessionId = frame.sessionId.ifBlank { sessionId }
            when (frame.payloadCase) {
                ClientFrame.PayloadCase.HELLO -> hello(frame.hello.protocolVersion)
                ClientFrame.PayloadCase.CHAT_REQUEST -> chat(frame.requestId, frame.chatRequest.content, frame.chatRequest.userEntryId)
                ClientFrame.PayloadCase.CANCEL_REQUEST -> cancel(frame.requestId, frame.cancelRequest)
                ClientFrame.PayloadCase.SNAPSHOT_ACK, ClientFrame.PayloadCase.PAYLOAD_NOT_SET -> Unit
            }
        }

        private fun hello(version: String) {
            if (version != ProtocolVersion.CURRENT) {
                error("INCOMPATIBLE_PROTOCOL", "Unsupported protocol version", retryable = false)
                return
            }
            helloReceived = true
            send(
                ServerFrame
                    .newBuilder()
                    .setSessionId(sessionId)
                    .setServerRevision(revision)
                    .setHelloAck(
                        HelloAck
                            .newBuilder()
                            .setProtocolVersion(ProtocolVersion.CURRENT)
                            .setServerName("visual-agent-server")
                            .setServerVersion("unknown")
                            .build(),
                    ).setSnapshot(
                        Snapshot
                            .newBuilder()
                            .setRevision(revision)
                            .setJson("{\"ready\":true}")
                            .build(),
                    ).build(),
            )
        }

        private fun chat(
            requestId: String,
            content: String,
            userEntryId: String,
        ) {
            if (!helloReceived) {
                error("SESSION_NOT_READY", "The session must complete the handshake first", retryable = false)
                return
            }
            if (content.isBlank()) {
                error("OPERATION_FAILED", "Chat content must not be blank", retryable = false)
                return
            }
            val request =
                runCatching { ConversationStreamRequest(userEntryId, requestId, content) }
                    .getOrElse { error ->
                        error(
                            "INVALID_ARGUMENT",
                            error.message ?: "Invalid conversation entry identity",
                            retryable = false,
                            requestId = requestId,
                        )
                        return
                    }
            cancelActiveRequest()
            val state = RequestState(requestId = requestId, token = CancellationTokenImpl())
            val job =
                scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        conversationPort.stream(request, state.token) { chunk -> sendDelta(state.requestId, chunk) }
                        send(
                            ServerFrame
                                .newBuilder()
                                .setSessionId(sessionId)
                                .setRequestId(state.requestId)
                                .setServerRevision(++revision)
                                .setChatCompleted(ChatCompleted.newBuilder().setSuccessful(true).build())
                                .build(),
                        )
                    } catch (_: CancellationException) {
                        error("CANCELLED", "Request cancelled", retryable = true, requestId = state.requestId)
                    } catch (_: Exception) {
                        error(
                            "OPERATION_FAILED",
                            "The server could not complete the request",
                            retryable = true,
                            requestId = state.requestId,
                        )
                    } finally {
                        if (activeRequest === state) activeRequest = null
                    }
                }
            state.job = job
            activeRequest = state
            job.start()
        }

        private fun cancel(
            requestId: String,
            request: CancelRequest,
        ) {
            if (!helloReceived) {
                error("SESSION_NOT_READY", "The session must complete the handshake first", retryable = false)
                return
            }
            cancelActiveRequest(requestId)
            if (request.reason.isNotBlank()) {
                error("CANCELLED", "Request cancelled", retryable = true, requestId = requestId)
            }
        }

        private fun sendDelta(
            requestId: String,
            text: String,
        ) {
            send(
                ServerFrame
                    .newBuilder()
                    .setSessionId(sessionId)
                    .setRequestId(requestId)
                    .setServerRevision(revision)
                    .setChatDelta(ChatDelta.newBuilder().setText(text).build())
                    .build(),
            )
        }

        private fun error(
            code: String,
            message: String,
            retryable: Boolean,
            requestId: String = "",
        ) {
            send(
                ServerFrame
                    .newBuilder()
                    .setSessionId(sessionId)
                    .setRequestId(requestId)
                    .setServerRevision(revision)
                    .setError(
                        OperationError
                            .newBuilder()
                            .setCode(code)
                            .setMessage(message)
                            .setRetryable(retryable)
                            .build(),
                    ).build(),
            )
        }

        private fun send(frame: ServerFrame) {
            synchronized(responseObserver) {
                runCatching { responseObserver.onNext(frame) }
            }
        }

        private fun cancelActiveRequest(requestId: String? = null) {
            val current = activeRequest ?: return
            if (requestId != null && requestId.isNotBlank() && current.requestId != requestId) return
            if (activeRequest === current) activeRequest = null
            current.token.cancel()
            current.job?.cancel()
        }

        /** Cancels work when the transport reports a connection failure. */
        fun close(
            @Suppress("UNUSED_PARAMETER") cause: Throwable,
        ) {
            cancelActiveRequest()
        }

        /** Cancels work and closes the response stream when the client completes it. */
        fun complete() {
            cancelActiveRequest()
            synchronized(responseObserver) {
                runCatching { responseObserver.onCompleted() }
            }
        }
    }

    private class RequestState(
        val requestId: String,
        val token: CancellationTokenImpl,
        var job: Job? = null,
    )
}
