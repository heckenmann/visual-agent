package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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

    @Test
    fun `generates a constrained CA key entry without returning private key material`() {
        val service = ManagedTlsMaterialService(temporaryDirectory)

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
        val service = ManagedTlsMaterialService(temporaryDirectory)
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
        val service = ManagedTlsMaterialService(temporaryDirectory)
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
        val service = ManagedTlsMaterialService(temporaryDirectory)
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
        val service = ManagedTlsMaterialService(temporaryDirectory)

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
        val service = ManagedTlsMaterialService(temporaryDirectory)
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
    fun `managed CA is accepted by additive trust manager and key entry creates a JDK key manager`() {
        val service = ManagedTlsMaterialService(temporaryDirectory)
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
        val service = ManagedTlsMaterialService(temporaryDirectory)
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
