package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.v1.ServerFrame
import de.heckenmann.visualagent.server.VisualAgentGrpcServer
import de.heckenmann.visualagent.server.VisualAgentGrpcSessionService
import io.grpc.stub.StreamObserver
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Verifies the desktop-to-server in-process transport without a network socket. */
class DesktopServerConnectionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val server =
        VisualAgentGrpcServer(
            VisualAgentGrpcSessionService(mockk<ConversationPort>(relaxed = true), scope),
            "desktop-connection-test",
            0,
            "",
            "",
        )

    @AfterTest
    fun closeResources() {
        server.close()
        scope.cancel()
    }

    @Test
    fun `local connection negotiates protocol and receives snapshot`() {
        server.start()
        DesktopServerConnection(DesktopServerEndpoint.LocalInProcess(server.inProcessServerName())).use { connection ->
            val observer = RecordingObserver<ServerFrame>()
            connection.openReadySession(observer)

            assertEquals(1, observer.values.size)
            assertTrue(observer.values.single().hasHelloAck())
            assertTrue(observer.values.single().hasSnapshot())
        }
    }

    @Test
    fun `remote connection creates a TLS channel without local fallback`() {
        DesktopServerConnection(DesktopServerEndpoint.RemoteTls("localhost", 7443)).close()
    }

    @Test
    fun `local application connection exposes ports only after readiness`() {
        runBlocking {
            val application = mockk<ApplicationPort>(relaxed = true)
            val connection = LocalApplicationConnection(server) { application }

            assertFailsWith<IllegalStateException> { connection.application }
            server.start()
            connection.awaitReady()
            assertSame(application, connection.application)

            connection.close()
            assertFailsWith<IllegalStateException> { connection.application }
        }
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
