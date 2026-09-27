package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.security.ManagedTlsCertificateOperations
import de.heckenmann.visualagent.security.ManagedTlsMaterialService
import de.heckenmann.visualagent.security.ManagedTlsStoreFiles
import de.heckenmann.visualagent.security.TlsMutationAudit
import io.mockk.mockk
import org.junit.jupiter.api.io.TempDir
import java.net.ServerSocket
import java.nio.file.Path
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies lifecycle ownership of the standalone in-process server. */
class VisualAgentGrpcServerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val server =
        VisualAgentGrpcServer(
            VisualAgentGrpcSessionService(mockk<ConversationPort>(relaxed = true)),
            "test-visual-agent",
            0,
            "",
            "",
        )

    @AfterTest
    fun closeServer() {
        server.close()
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
                VisualAgentGrpcSessionService(mockk<ConversationPort>(relaxed = true)),
                "test-exposed-server",
                7443,
                "certificate.pem",
                "private-key.pem",
                "0.0.0.0",
            )

        assertFailsWith<IllegalArgumentException> { exposedServer.start() }
        exposedServer.close()
    }

    @Test
    fun `loopback grpc endpoint presents selected managed certificate with valid hostname`() {
        val files = ManagedTlsStoreFiles(temporaryDirectory)
        val audit = TlsMutationAudit()
        val tlsMaterial = ManagedTlsMaterialService(files, audit, ManagedTlsCertificateOperations(files, audit))
        val ca = tlsMaterial.generateCertificate("test-ca", "CN=Test CA", emptyList(), emptyList(), true, 30)
        tlsMaterial.generateCertificate("server", "CN=localhost", emptyList(), listOf("127.0.0.1"), false, 30, ca.alias)
        tlsMaterial.importTrustedCertificate("test-ca", tlsMaterial.exportPublicCertificate(ca.alias)!!)
        val port = ServerSocket(0).use { it.localPort }
        val remoteServer =
            VisualAgentGrpcServer(
                VisualAgentGrpcSessionService(mockk<ConversationPort>(relaxed = true)),
                "test-managed-tls-server",
                port,
                "",
                "",
                "127.0.0.1",
                "server",
                tlsMaterial,
            )

        try {
            remoteServer.start()
            val clientContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tlsMaterial.trustManager()), null) }
            val socket = clientContext.socketFactory.createSocket("127.0.0.1", port) as SSLSocket
            socket.use {
                it.sslParameters = it.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                it.startHandshake()
            }
        } finally {
            remoteServer.close()
        }
    }
}
