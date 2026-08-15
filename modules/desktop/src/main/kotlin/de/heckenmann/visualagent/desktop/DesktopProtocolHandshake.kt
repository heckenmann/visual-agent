package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.v1.ServerFrame
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/** Performs the transport-neutral hello/readiness exchange for a desktop connection. */
internal suspend fun awaitProtocolHandshake(endpoint: DesktopServerEndpoint) {
    val ready = CompletableDeferred<Unit>()
    val observer = HandshakeObserver(ready)
    val connection = DesktopServerConnection(endpoint)
    val session =
        try {
            connection.openReadySession(observer)
        } catch (failure: Throwable) {
            connection.close()
            throw failure
        }
    try {
        withTimeout(HANDSHAKE_TIMEOUT_MILLIS) { ready.await() }
    } finally {
        session.close()
        connection.close()
    }
}

private class HandshakeObserver(
    private val ready: CompletableDeferred<Unit>,
) : StreamObserver<ServerFrame> {
    private var helloAcknowledged = false
    private var snapshotReceived = false

    override fun onNext(value: ServerFrame) {
        helloAcknowledged = helloAcknowledged || value.hasHelloAck()
        snapshotReceived = snapshotReceived || value.hasSnapshot()
        when {
            helloAcknowledged && snapshotReceived -> ready.complete(Unit)
            value.hasError() -> ready.completeExceptionally(IllegalStateException("Protocol handshake failed"))
        }
    }

    override fun onError(t: Throwable) {
        ready.completeExceptionally(t)
    }

    override fun onCompleted() {
        if (!ready.isCompleted) {
            ready.completeExceptionally(IllegalStateException("Server closed the session"))
        }
    }
}

private const val HANDSHAKE_TIMEOUT_MILLIS = 10_000L
