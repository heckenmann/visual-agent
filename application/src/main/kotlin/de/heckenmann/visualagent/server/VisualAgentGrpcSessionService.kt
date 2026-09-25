package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import reactor.core.Disposable
import java.util.concurrent.atomic.AtomicBoolean

/** Bridges one bidirectional protocol session to the application services. */
@Component
class VisualAgentGrpcSessionService(
    private val conversationPort: ConversationPort,
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
            cancelActiveRequest(notifyClient = true)
            val state = RequestState(requestId = requestId, token = CancellationTokenImpl())
            activeRequest = state
            state.subscription =
                mono(Dispatchers.IO) {
                    conversationPort.stream(request, state.token) { update -> sendDelta(state.requestId, update) }
                }.doOnCancel(state.token::cancel)
                    .subscribe(
                        {
                            if (state.token.isCancelled) {
                                sendCancellation(state)
                            } else if (state.terminal.compareAndSet(false, true)) {
                                send(
                                    ServerFrame
                                        .newBuilder()
                                        .setSessionId(sessionId)
                                        .setRequestId(state.requestId)
                                        .setServerRevision(++revision)
                                        .setChatCompleted(ChatCompleted.newBuilder().setSuccessful(true).build())
                                        .build(),
                                )
                            }
                            clearActiveRequest(state)
                        },
                        { throwable ->
                            if (throwable is CancellationException || state.token.isCancelled) {
                                sendCancellation(state)
                            } else if (state.terminal.compareAndSet(false, true)) {
                                error(
                                    "OPERATION_FAILED",
                                    "The server could not complete the request",
                                    retryable = true,
                                    requestId = state.requestId,
                                )
                            }
                            clearActiveRequest(state)
                        },
                    )
            if (activeRequest !== state || state.token.isCancelled) state.subscription?.dispose()
        }

        private fun cancel(
            requestId: String,
            request: CancelRequest,
        ) {
            if (!helloReceived) {
                error("SESSION_NOT_READY", "The session must complete the handshake first", retryable = false)
                return
            }
            val cancelled = cancelActiveRequest(requestId, notifyClient = request.reason.isNotBlank())
            if (!cancelled && request.reason.isNotBlank()) {
                error("CANCELLED", "Request cancelled", retryable = true, requestId = requestId)
            }
        }

        private fun sendDelta(
            requestId: String,
            update: ConversationStreamUpdate,
        ) {
            send(
                ServerFrame
                    .newBuilder()
                    .setSessionId(sessionId)
                    .setRequestId(requestId)
                    .setServerRevision(revision)
                    .setChatDelta(
                        ChatDelta
                            .newBuilder()
                            .setText(update.textDelta)
                            .setAssistantTurnId(update.assistantTurnId)
                            .build(),
                    ).build(),
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

        private fun sendCancellation(state: RequestState) {
            if (!state.terminal.compareAndSet(false, true)) return
            error("CANCELLED", "Request cancelled", retryable = true, requestId = state.requestId)
        }

        private fun send(frame: ServerFrame) {
            synchronized(responseObserver) {
                runCatching { responseObserver.onNext(frame) }
            }
        }

        private fun cancelActiveRequest(
            requestId: String? = null,
            notifyClient: Boolean = false,
        ): Boolean {
            val current = activeRequest ?: return false
            if (requestId != null && requestId.isNotBlank() && current.requestId != requestId) return false
            if (activeRequest === current) activeRequest = null
            if (notifyClient) sendCancellation(current)
            current.token.cancel()
            current.subscription?.dispose()
            return true
        }

        private fun clearActiveRequest(state: RequestState) {
            if (activeRequest === state) activeRequest = null
        }

        /** Cancels work when the transport reports a connection failure. */
        fun close(_cause: Throwable) {
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
        val terminal: AtomicBoolean = AtomicBoolean(false),
        var subscription: Disposable? = null,
    )
}
