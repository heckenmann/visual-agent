package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ConversationPort
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies lifecycle ownership of the standalone in-process server. */
class VisualAgentGrpcServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val server =
        VisualAgentGrpcServer(
            VisualAgentGrpcSessionService(mockk<ConversationPort>(relaxed = true), scope),
            "test-visual-agent",
            0,
            "",
            "",
        )

    @AfterTest
    fun closeServer() {
        server.close()
        scope.cancel()
    }

    @Test
    fun `in process endpoint becomes ready and closes cleanly`() {
        assertFalse(server.isReady())

        server.start()
        server.start()

        assertTrue(server.isReady())
        assertFalse(server.inProcessServerName().isBlank())
        server.close()
        assertFalse(server.isReady())
    }

    @Test
    fun `non loopback network binding is rejected`() {
        val exposedServer =
            VisualAgentGrpcServer(
                VisualAgentGrpcSessionService(mockk<ConversationPort>(relaxed = true), scope),
                "test-exposed-server",
                7443,
                "certificate.pem",
                "private-key.pem",
                "0.0.0.0",
            )

        assertFailsWith<IllegalArgumentException> { exposedServer.start() }
        exposedServer.close()
    }
}
