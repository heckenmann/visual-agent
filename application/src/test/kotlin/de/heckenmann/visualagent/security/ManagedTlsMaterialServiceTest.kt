package de.heckenmann.visualagent.security

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509KeyManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ManagedTlsMaterialServiceTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private fun service(root: Path): ManagedTlsMaterialService {
        val files = ManagedTlsStoreFiles(root)
        val audit = TlsMutationAudit()
        return ManagedTlsMaterialService(files, audit, ManagedTlsCertificateOperations(files, audit))
    }

    @Test
    fun `generates a constrained CA key entry without returning private key material`() {
        val service = service(temporaryDirectory)

        val generated = service.generateCertificate("test-root", "CN=Test Root CA", emptyList(), emptyList(), true, 30)

        assertTrue(generated.certificateAuthority)
        assertTrue(generated.restartRequired)
        assertTrue(generated.certificate.basicConstraints >= 0)
        assertNotNull(service.inspect(ServerTlsStore.KEY, "test-root", false))
        assertTrue(service.inspect(ServerTlsStore.KEY, "test-root", false)!!.entryType == "private_key")
        assertTrue(Files.exists(temporaryDirectory.resolve("security/tls/keystore.p12")))
        assertTrue(Files.exists(temporaryDirectory.resolve("security/tls/keystore.p12.backup")))
    }

    @Test
    fun `generates TLS certificate with DNS and IP subject alternative names`() {
        val service = service(temporaryDirectory)
        service.generateCertificate("root-ca", "CN=Test Root CA", emptyList(), emptyList(), true, 30)

        val result =
            service.generateCertificate(
                "server-cert",
                "CN=agent.example.test",
                listOf("agent.example.test"),
                listOf("127.0.0.1"),
                false,
                30,
                "root-ca",
            )

        assertEquals(listOf("dns:agent.example.test", "ip:127.0.0.1"), result.certificate.subjectAlternativeNames)
        assertEquals(-1, result.certificate.basicConstraints)
        val chain = service.inspect(ServerTlsStore.KEY, "server-cert", false)!!.certificates
        assertEquals(2, chain.size)
        assertEquals("CN=Test Root CA", result.certificate.issuer)
        assertTrue(service.exportPublicCertificate("server-cert").orEmpty().startsWith("-----BEGIN CERTIFICATE-----"))
    }

    @Test
    fun `imports only valid CA certificates and removes the exact managed alias`() {
        val service = service(temporaryDirectory)
        val ca = service.generateCertificate("trusted-root", "CN=Trusted Root", emptyList(), emptyList(), true, 30)
        val caPem = service.exportPublicCertificate(ca.alias)!!
        assertIllegalArgument { service.importTrustedCertificate("binary-input", "\u0000$caPem") }
        val trustEntry = service.importTrustedCertificate("private-ca", caPem)

        assertEquals("private-ca", trustEntry.alias)
        assertEquals("trusted_certificate", trustEntry.entryType)
        assertEquals(ca.certificate.sha256, trustEntry.certificates.single().sha256)
        assertTrue(service.removeTrustedCertificate("private-ca"))
        assertFalse(service.removeTrustedCertificate("private-ca"))
    }

    @Test
    fun `rejects non-CA certificate and duplicate trust alias`() {
        val service = service(temporaryDirectory)
        service.generateCertificate("root-ca", "CN=Test Root CA", emptyList(), emptyList(), true, 30)
        val leaf =
            service.generateCertificate(
                "leaf",
                "CN=leaf.example.test",
                listOf("leaf.example.test"),
                emptyList(),
                false,
                30,
                "root-ca",
            )
        val pem = service.exportPublicCertificate(leaf.alias)!!

        assertIllegalArgument { service.importTrustedCertificate("leaf-ca", pem) }
        val ca = service.generateCertificate("root", "CN=Root", emptyList(), emptyList(), true, 30)
        val caPem = service.exportPublicCertificate(ca.alias)!!
        service.importTrustedCertificate("duplicate", caPem)

        assertIllegalArgument { service.importTrustedCertificate("duplicate", caPem) }
    }

    @Test
    fun `rejects path traversal aliases malformed subjects and server certificates without SANs`() {
        val service = service(temporaryDirectory)

        assertIllegalArgument { service.inspect(ServerTlsStore.TRUST, "../outside", false) }
        assertIllegalArgument { service.generateCertificate("../outside", "CN=bad", emptyList(), emptyList(), true, 30) }
        assertIllegalArgument {
            service.generateCertificate(
                "bad-subject",
                "not a DN",
                listOf("agent.example.test"),
                emptyList(),
                false,
                30,
            )
        }
        assertIllegalArgument { service.generateCertificate("missing-san", "CN=agent.example.test", emptyList(), emptyList(), false, 30) }
        assertIllegalArgument {
            service.generateCertificate("bad-dns", "CN=agent.example.test", listOf("https://example.test"), emptyList(), false, 30)
        }
        assertIllegalArgument {
            service.generateCertificate("too-long", "CN=agent.example.test", listOf("agent.example.test"), emptyList(), false, 826)
        }
    }

    @Test
    fun `uses separate restricted password files and preserves the JDK trust store`() {
        val service = service(temporaryDirectory)
        service.list(ServerTlsStore.TRUST)
        service.generateCertificate("root-ca", "CN=Test Root CA", emptyList(), emptyList(), true, 30)
        service.generateCertificate("local-server", "CN=localhost", listOf("localhost"), listOf("127.0.0.1"), false, 30, "root-ca")
        val tlsDirectory = temporaryDirectory.resolve("security/tls")
        val trustPassword = Files.readString(tlsDirectory.resolve(".truststore-password")).trim()
        val keyPassword = Files.readString(tlsDirectory.resolve(".keystore-password")).trim()

        assertTrue(trustPassword.length >= 40)
        assertTrue(keyPassword.length >= 40)
        assertFalse(Files.exists(temporaryDirectory.resolve("security/tls/cacerts")))
        assertNull(service.exportPublicCertificate("unknown"))
        assertFalse(service.removeKeyEntry("unknown"))
    }

    @Test
    fun `recovers the latest managed store after the primary store is corrupted`() {
        val service = service(temporaryDirectory)
        service.generateCertificate("root-ca", "CN=Test Root CA", emptyList(), emptyList(), true, 30)
        service.generateCertificate(
            "server-cert",
            "CN=agent.example.test",
            listOf("agent.example.test"),
            emptyList(),
            false,
            30,
            "root-ca",
        )
        val keyStorePath = temporaryDirectory.resolve("security/tls/keystore.p12")
        Files.writeString(keyStorePath, "corrupt-store")

        val restartedService = service(temporaryDirectory)

        assertNotNull(restartedService.inspect(ServerTlsStore.KEY, "root-ca", false))
        assertNotNull(restartedService.inspect(ServerTlsStore.KEY, "server-cert", false))
        assertNotNull(restartedService.inspect(ServerTlsStore.KEY, "root-ca", false))
    }

    @Test
    fun `recovers the latest managed store when the primary store is missing`() {
        val service = service(temporaryDirectory)
        service.generateCertificate("root-ca", "CN=Test Root CA", emptyList(), emptyList(), true, 30)
        service.generateCertificate("secondary-ca", "CN=Secondary CA", emptyList(), emptyList(), true, 30)
        Files.delete(temporaryDirectory.resolve("security/tls/keystore.p12"))

        val restartedService = service(temporaryDirectory)

        assertNotNull(restartedService.inspect(ServerTlsStore.KEY, "root-ca", false))
        assertNotNull(restartedService.inspect(ServerTlsStore.KEY, "secondary-ca", false))
        assertTrue(Files.exists(temporaryDirectory.resolve("security/tls/keystore.p12")))
    }

    @Test
    fun `recovers the latest trust store when its primary file is missing`() {
        val service = service(temporaryDirectory)
        service.generateCertificate("root-ca", "CN=Test Root CA", emptyList(), emptyList(), true, 30)
        service.generateCertificate("secondary-ca", "CN=Secondary CA", emptyList(), emptyList(), true, 30)
        service.importTrustedCertificate("trusted-root", service.exportPublicCertificate("root-ca")!!)
        service.importTrustedCertificate("trusted-secondary", service.exportPublicCertificate("secondary-ca")!!)
        Files.delete(temporaryDirectory.resolve("security/tls/truststore.p12"))

        val restartedService = service(temporaryDirectory)

        assertNotNull(restartedService.inspect(ServerTlsStore.TRUST, "trusted-root", false))
        assertNotNull(restartedService.inspect(ServerTlsStore.TRUST, "trusted-secondary", false))
        assertTrue(Files.exists(temporaryDirectory.resolve("security/tls/truststore.p12")))
    }

    @Test
    fun `managed CA is accepted by additive trust manager and key entry creates a JDK key manager`() {
        val service = service(temporaryDirectory)
        val ca = service.generateCertificate("root-ca", "CN=Test Root CA", emptyList(), emptyList(), true, 30)
        service.generateCertificate("server", "CN=localhost", listOf("localhost"), emptyList(), false, 30, "root-ca")
        val caCertificate = parseCertificate(service.exportPublicCertificate(ca.alias)!!)
        service.importTrustedCertificate("trusted-root", service.exportPublicCertificate(ca.alias)!!)

        service
            .trustBundle()
            .managers.trustManagers
            .filterIsInstance<javax.net.ssl.X509TrustManager>()
            .single()
            .checkServerTrusted(arrayOf(caCertificate), caCertificate.publicKey.algorithm)
        assertNotNull(service.trustBundle().createSslContext())
        assertTrue(
            service
                .keyBundle("server")
                .managers.keyManagerFactory.keyManagers
                .isNotEmpty(),
        )
        assertNotNull(service.keyBundle("server").createSslContext())
    }

    @Test
    fun `key bundle exposes only the selected server alias`() {
        val service = service(temporaryDirectory)
        service.generateCertificate("root-ca", "CN=Test Root CA", emptyList(), emptyList(), true, 30)
        service.generateCertificate(
            "first-server",
            "CN=first.example.test",
            listOf("first.example.test"),
            emptyList(),
            false,
            30,
            "root-ca",
        )
        service.generateCertificate(
            "selected-server",
            "CN=selected.example.test",
            listOf("selected.example.test"),
            emptyList(),
            false,
            30,
            "root-ca",
        )

        val keyManager =
            service
                .keyBundle(
                    "selected-server",
                ).managers.keyManagerFactory.keyManagers
                .filterIsInstance<X509KeyManager>()
                .single()

        assertEquals(listOf("selected-server"), keyManager.getServerAliases("RSA", null).orEmpty().toList())
    }

    @Test
    fun `store mutations log operation alias fingerprint and outcome without material`() {
        val service = service(temporaryDirectory)
        val logger = LoggerFactory.getLogger(TlsMutationAudit::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            val generated = service.generateCertificate("audit-root", "CN=Audit Root", emptyList(), emptyList(), true, 30)
            assertFalse(service.removeKeyEntry("missing-entry"))
            assertIllegalArgument { service.importTrustedCertificate("bad-cert", "PRIVATE-MARKER is not a certificate") }

            val events = appender.list.map { it.formattedMessage }
            assertTrue(
                events.any {
                    it.contains("operation=generate_certificate") &&
                        it.contains(generated.certificate.sha256) &&
                        it.contains("outcome=success")
                },
            )
            assertTrue(
                events.any {
                    it.contains("operation=remove_key_entry") &&
                        it.contains("alias=missing-entry") &&
                        it.contains("outcome=not_found")
                },
            )
            assertTrue(events.any { it.contains("operation=import_certificate") && it.contains("outcome=failed") })
            assertTrue(events.none { it.contains("PRIVATE-MARKER") || it.contains("-----BEGIN CERTIFICATE-----") })
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected for a rejected certificate or alias.
        }
    }

    private fun parseCertificate(pem: String): X509Certificate =
        CertificateFactory
            .getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem.toByteArray(StandardCharsets.US_ASCII))) as X509Certificate
}
