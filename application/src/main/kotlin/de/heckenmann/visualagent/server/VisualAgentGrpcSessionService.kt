package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ConversationPort
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
        private var requestId = ""
        private var revision = 0L
        private var requestJob: Job? = null
        private var cancellationToken: CancellationTokenImpl? = null
        private var helloReceived = false

        /** Handles one client frame without exposing application services to the transport. */
        fun accept(frame: ClientFrame) {
            sessionId = frame.sessionId.ifBlank { sessionId }
            requestId = frame.requestId.ifBlank { requestId }
            when (frame.payloadCase) {
                ClientFrame.PayloadCase.HELLO -> hello(frame.hello.protocolVersion)
                ClientFrame.PayloadCase.CHAT_REQUEST -> chat(frame.chatRequest.content)
                ClientFrame.PayloadCase.CANCEL_REQUEST -> cancel(frame.cancelRequest)
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

        private fun chat(content: String) {
            if (!helloReceived) {
                error("SESSION_NOT_READY", "The session must complete the handshake first", retryable = false)
                return
            }
            if (content.isBlank()) {
                error("OPERATION_FAILED", "Chat content must not be blank", retryable = false)
                return
            }
            cancelActiveRequest()
            val token = CancellationTokenImpl()
            cancellationToken = token
            requestJob =
                scope.launch {
                    try {
                        conversationPort.stream(content, token) { chunk -> sendDelta(chunk) }
                        send(
                            ServerFrame
                                .newBuilder()
                                .setSessionId(sessionId)
                                .setRequestId(requestId)
                                .setServerRevision(++revision)
                                .setChatCompleted(ChatCompleted.newBuilder().setSuccessful(true).build())
                                .build(),
                        )
                    } catch (_: CancellationException) {
                        error("CANCELLED", "Request cancelled", retryable = true)
                    } catch (_: Exception) {
                        error("OPERATION_FAILED", "The server could not complete the request", retryable = true)
                    } finally {
                        requestJob = null
                        cancellationToken = null
                    }
                }
        }

        private fun cancel(request: CancelRequest) {
            if (!helloReceived) {
                error("SESSION_NOT_READY", "The session must complete the handshake first", retryable = false)
                return
            }
            cancelActiveRequest()
            if (request.reason.isNotBlank()) {
                error("CANCELLED", "Request cancelled", retryable = true)
            }
        }

        private fun sendDelta(text: String) {
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

        private fun cancelActiveRequest() {
            cancellationToken?.cancel()
            requestJob?.cancel()
            requestJob = null
            cancellationToken = null
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
}
