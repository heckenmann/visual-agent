package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.HostResolver
import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import de.heckenmann.visualagent.protocol.PresentedCertificateKind
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import de.heckenmann.visualagent.server.SpringCertificateTrustPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetAddress
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises review, confirmation, and actual JSSE connections with a private intermediate chain. */
class ReviewedTlsCertificatesTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `server certificate survives reopen and applies only to its exact endpoint`() {
        val fixture = certificateFixture()
        val material = material()
        val resolver = mockk<HostResolver>()
        every { resolver.resolve("localhost", null) } returns
            listOf(InetAddress.getByName("127.0.0.2"), InetAddress.getByName("127.0.0.1"))
        val admin = SpringCertificateTrustPort(material, PresentedTlsCertificates(resolver))
        val platform = material.trustSnapshot()
        withEndpoint(fixture) { port ->
            withEndpoint(fixture) { otherPort ->
                val url = "https://localhost:$port/v1"
                val presented = admin.reviewEndpoint(url)
                assertEquals(
                    listOf(PresentedCertificateKind.SERVER, PresentedCertificateKind.INTERMEDIATE_CA, PresentedCertificateKind.ROOT_CA),
                    presented.certificates.map { it.kind },
                )
                val reviewed = presented.certificates.map { it.certificate.sha256 }
                assertEquals(3, reviewed.distinct().size)
                assertTrue(reviewed.all { it.matches(Regex("[0-9a-f]{64}")) })
                assertTrue(admin.list().isEmpty())
                assertFailsWith<ProtocolOperationException> {
                    admin.importEndpointCertificates(url, listOf("0".repeat(64)) + reviewed.drop(1), setOf(reviewed.last()))
                }
                assertFailsWith<ProtocolOperationException> {
                    admin.importEndpointCertificates(url, reviewed, setOf("0".repeat(64)))
                }
                assertTrue(admin.list().isEmpty())

                admin.importEndpointCertificates(url, reviewed, setOf(reviewed.first()))
                val restarted = material()
                val trust = restarted.trustSnapshot()
                val restoredAdmin = SpringCertificateTrustPort(restarted, PresentedTlsCertificates())
                val saved = restoredAdmin.list().single()
                assertEquals("localhost:$port", saved.endpoint)
                assertEquals(reviewed.first(), saved.sha256)
                assertTrue(assertNotNull(restoredAdmin.exportCertificate(saved.alias)).startsWith("-----BEGIN CERTIFICATE-----"))
                assertEquals(platform.manager.acceptedIssuers.toSet(), trust.manager.acceptedIssuers.toSet())
                assertNotEquals(platform.revision, trust.revision)
                assertTrue(restarted.list(ServerTlsStore.TRUST).isEmpty())
                assertFalse(assertNotNull(restarted.trustBundle().stores.trustStore).containsAlias(saved.alias))

                assertTrue(accepts(trust.manager, "localhost", port))
                assertFalse(accepts(trust.manager, "localhost", otherPort))
                assertFalse(accepts(trust.manager, "127.0.0.1", port))
                assertFalse(restoredAdmin.removeCertificate(saved.alias, "0".repeat(64)))
                assertTrue(restoredAdmin.removeCertificate(saved.alias, saved.sha256))
                assertEquals(platform.revision, restarted.trustSnapshot().revision)
                assertFalse(accepts(restarted.trustManager(), "localhost", port))
            }
        }
    }

    @Test
    fun `intermediate can be imported when the endpoint omits its root and hostname still applies`() {
        val fixture = certificateFixture(includeIpSan = false)
        val material = material()
        val admin = SpringCertificateTrustPort(material, PresentedTlsCertificates())
        withEndpoint(fixture, omitRoot = true) { port ->
            val url = "https://localhost:$port/v1"
            val presented = admin.reviewEndpoint(url)
            assertEquals(
                listOf(PresentedCertificateKind.SERVER, PresentedCertificateKind.INTERMEDIATE_CA),
                presented.certificates.map { it.kind },
            )
            val fingerprints = presented.certificates.map { it.certificate.sha256 }
            assertFailsWith<ProtocolOperationException> {
                admin.importEndpointCertificates("https://127.0.0.1:$port", fingerprints, setOf(fingerprints.last()))
            }
            assertTrue(admin.list().isEmpty())
            admin.importEndpointCertificates(url, fingerprints, fingerprints.toSet())
            assertEquals(setOf(null, "localhost:$port"), admin.list().map { it.endpoint }.toSet())
            assertTrue(
                material
                    .list(ServerTlsStore.TRUST)
                    .single()
                    .certificates
                    .single()
                    .sha256 == fingerprints.last(),
            )
            assertTrue(accepts(material.trustManager(), "localhost", port))
        }
    }

    private fun material(): ManagedTlsMaterialService {
        val files = ManagedTlsStoreFiles(directory.resolve("application"))
        val audit = TlsMutationAudit()
        return ManagedTlsMaterialService(files, audit, ManagedTlsCertificateOperations(files, audit))
    }

    @Test
    fun `root alone cannot repair an expired intermediate but the explicitly selected server certificate can`() {
        val fixture = certificateFixture()
        val expiredReply = directory.resolve("expired-intermediate.pem")
        keytool(
            fixture,
            "-gencert",
            "-alias",
            "root",
            "-infile",
            directory.resolve("intermediate.csr").toString(),
            "-outfile",
            expiredReply.toString(),
            "-startdate",
            "-2d",
            "-validity",
            "1",
            "-ext",
            "BC=ca:true,pathlen:0",
            "-ext",
            "KU=keyCertSign,cRLSign",
        )
        val material = material()
        val admin = SpringCertificateTrustPort(material, PresentedTlsCertificates())
        withEndpoint(fixture, intermediateReplacement = expiredReply) { port ->
            val url = "https://localhost:$port"
            val reviewed = admin.reviewEndpoint(url)
            assertFalse(reviewed.certificates.single { it.kind == PresentedCertificateKind.INTERMEDIATE_CA }.canImport)
            val fingerprints = reviewed.certificates.map { it.certificate.sha256 }
            assertFailsWith<ProtocolOperationException> {
                admin.importEndpointCertificates(url, fingerprints, setOf(fingerprints.last()))
            }
            assertTrue(admin.list().isEmpty())
            admin.importEndpointCertificates(url, fingerprints, setOf(fingerprints.first()))
            assertTrue(accepts(material.trustManager(), "localhost", port))
        }
    }

    private fun certificateFixture(includeIpSan: Boolean = true): ManagedTlsStoreFiles {
        val files = ManagedTlsStoreFiles(directory.resolve("endpoint"))
        val audit = TlsMutationAudit()
        val operations = ManagedTlsCertificateOperations(files, audit)
        operations.generate("root", "CN=Private Root", emptyList(), emptyList(), true, 30, null)
        operations.generate("intermediate", "CN=Private Issuer", emptyList(), emptyList(), true, 30, null)
        val request = directory.resolve("intermediate.csr")
        val reply = directory.resolve("intermediate.pem")
        keytool(files, "-certreq", "-alias", "intermediate", "-file", request.toString())
        keytool(
            files,
            "-gencert",
            "-alias",
            "root",
            "-infile",
            request.toString(),
            "-outfile",
            reply.toString(),
            "-validity",
            "25",
            "-ext",
            "BC=ca:true,pathlen:0",
            "-ext",
            "KU=keyCertSign,cRLSign",
        )
        keytool(files, "-importcert", "-alias", "intermediate", "-file", reply.toString(), "-noprompt")
        val ipAddresses = if (includeIpSan) listOf("127.0.0.1") else emptyList()
        operations.generate("server", "CN=localhost", listOf("localhost"), ipAddresses, false, 20, "intermediate")
        return files
    }

    private fun keytool(
        files: ManagedTlsStoreFiles,
        vararg args: String,
    ) {
        val executable = if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool"
        val process =
            ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                *args,
                "-keystore",
                files.storePath(ServerTlsStore.KEY).toString(),
                "-storepass:file",
                files.passwordFile(ServerTlsStore.KEY).toString(),
            ).redirectErrorStream(true).start()
        assertTrue(process.waitFor(15, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().use { it.readText() })
    }

    private fun withEndpoint(
        files: ManagedTlsStoreFiles,
        omitRoot: Boolean = false,
        intermediateReplacement: Path? = null,
        block: (Int) -> Unit,
    ) {
        val source = files.load(ServerTlsStore.KEY)
        val password = files.passwordChars(ServerTlsStore.KEY)
        val selection = KeyStore.getInstance("PKCS12").apply { load(null) }
        val chain = source.getCertificateChain("server")
        assertEquals(3, chain.size)
        if (intermediateReplacement != null) {
            chain[1] =
                java.nio.file.Files.newInputStream(intermediateReplacement).use {
                    CertificateFactory.getInstance("X.509").generateCertificate(it)
                }
        }
        selection.setKeyEntry(
            "server",
            source.getKey("server", password),
            password,
            if (omitRoot) chain.dropLast(1).toTypedArray() else chain,
        )
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(selection, password) }
        password.fill('\u0000')
        val tls = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        (tls.serverSocketFactory.createServerSocket(0, 16, InetAddress.getByName("127.0.0.1")) as SSLServerSocket).use { server ->
            val worker =
                thread(isDaemon = true) {
                    while (!server.isClosed) {
                        runCatching {
                            (server.accept() as SSLSocket).use { socket ->
                                socket.soTimeout = 5_000
                                socket.startHandshake()
                            }
                        }
                    }
                }
            try {
                block(server.localPort)
            } finally {
                server.close()
                worker.join(6_000)
            }
        }
    }

    private fun accepts(
        manager: X509TrustManager,
        host: String,
        port: Int,
    ): Boolean =
        runCatching {
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
            (tls.socketFactory.createSocket(host, port) as SSLSocket).use {
                it.soTimeout = 5_000
                it.sslParameters = it.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                it.startHandshake()
            }
        }.isSuccess
}
