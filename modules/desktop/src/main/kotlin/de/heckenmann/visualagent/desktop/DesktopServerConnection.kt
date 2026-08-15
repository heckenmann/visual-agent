package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.v1.ClientFrame
import de.heckenmann.visualagent.protocol.v1.ServerFrame
import de.heckenmann.visualagent.protocol.v1.VisualAgentSessionServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.StreamObserver
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Endpoint selected before the desktop contacts a Visual Agent server. */
sealed interface DesktopServerEndpoint {
    /** Local, same-JVM transport that does not expose a TCP listener. */
    data class LocalInProcess(
        val name: String,
    ) : DesktopServerEndpoint

    /** Remote TLS endpoint explicitly configured by the user or administrator. */
    data class RemoteTls(
        val host: String,
        val port: Int,
    ) : DesktopServerEndpoint
}

/** Owns the client channel and generated gRPC session stub for one desktop connection. */
class DesktopServerConnection(
    endpoint: DesktopServerEndpoint,
) : AutoCloseable {
    private val channel: ManagedChannel =
        when (endpoint) {
            is DesktopServerEndpoint.LocalInProcess ->
                InProcessChannelBuilder
                    .forName(endpoint.name)
                    .directExecutor()
                    .build()
            is DesktopServerEndpoint.RemoteTls ->
                NettyChannelBuilder
                    .forAddress(endpoint.host, endpoint.port)
                    .useTransportSecurity()
                    .build()
        }

    /** Opens the bidirectional session stream. */
    fun openSession(responseObserver: StreamObserver<ServerFrame>): StreamObserver<ClientFrame> =
        VisualAgentSessionServiceGrpc.newStub(channel).openSession(responseObserver)

    /** Opens a protocol session and sends the mandatory version handshake. */
    fun openReadySession(observer: StreamObserver<ServerFrame>): DesktopServerSession {
        val requestObserver = openSession(observer)
        val session = DesktopServerSession(requestObserver)
        session.sendHello()
        return session
    }

    override fun close() {
        channel.shutdownNow()
    }
}

/** Client-side operations for one negotiated Visual Agent protocol session. */
class DesktopServerSession internal constructor(
    private val requestObserver: StreamObserver<ClientFrame>,
    private val sessionId: String = UUID.randomUUID().toString(),
) : AutoCloseable {
    private val requestSequence = AtomicLong()

    /** Sends the protocol version and asks the server for its initial snapshot. */
    fun sendHello() {
        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId(sessionId)
                .setClientRevision(requestSequence.incrementAndGet())
                .setHello(
                    de.heckenmann.visualagent.protocol.v1.Hello
                        .newBuilder()
                        .setProtocolVersion(de.heckenmann.visualagent.protocol.ProtocolVersion.CURRENT)
                        .setClientName("visual-agent-desktop")
                        .build(),
                ).build(),
        )
    }

    /** Sends a user chat request and returns its generated request identifier. */
    fun sendChat(content: String): String {
        require(content.isNotBlank()) { "Chat content must not be blank" }
        val requestId = UUID.randomUUID().toString()
        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId(sessionId)
                .setRequestId(requestId)
                .setClientRevision(requestSequence.incrementAndGet())
                .setChatRequest(
                    de.heckenmann.visualagent.protocol.v1.ChatRequest
                        .newBuilder()
                        .setContent(content)
                        .build(),
                ).build(),
        )
        return requestId
    }

    /** Cancels the currently active request. */
    fun cancel(
        requestId: String,
        reason: String = "Cancelled by desktop",
    ) {
        requestObserver.onNext(
            ClientFrame
                .newBuilder()
                .setSessionId(sessionId)
                .setRequestId(requestId)
                .setClientRevision(requestSequence.incrementAndGet())
                .setCancelRequest(
                    de.heckenmann.visualagent.protocol.v1.CancelRequest
                        .newBuilder()
                        .setReason(reason)
                        .build(),
                ).build(),
        )
    }

    override fun close() {
        requestObserver.onCompleted()
    }
}
