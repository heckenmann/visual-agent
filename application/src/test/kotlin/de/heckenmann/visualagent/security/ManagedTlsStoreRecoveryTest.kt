package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedTlsStoreRecoveryTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `corrupt trust store recovery does not restore a removed CA`() {
        val service = service()
        service.generateCertificate("retained-ca", "CN=Retained CA", emptyList(), emptyList(), true, 30)
        service.generateCertificate("removed-ca", "CN=Removed CA", emptyList(), emptyList(), true, 30)
        service.importTrustedCertificate("retained", service.exportPublicCertificate("retained-ca")!!)
        service.importTrustedCertificate("removed", service.exportPublicCertificate("removed-ca")!!)
        assertTrue(service.removeTrustedCertificate("removed"))
        Files.writeString(temporaryDirectory.resolve("security/tls/truststore.p12"), "corrupt-store")

        val restartedService = service()

        assertNotNull(restartedService.inspect(ServerTlsStore.TRUST, "retained", false))
        assertNull(restartedService.inspect(ServerTlsStore.TRUST, "removed", false))
    }

    @Test
    fun `missing key store recovery does not restore a removed private key`() {
        val service = service()
        service.generateCertificate("retained-ca", "CN=Retained CA", emptyList(), emptyList(), true, 30)
        service.generateCertificate("removed-ca", "CN=Removed CA", emptyList(), emptyList(), true, 30)
        assertTrue(service.removeKeyEntry("removed-ca"))
        Files.delete(temporaryDirectory.resolve("security/tls/keystore.p12"))

        val restartedService = service()

        assertNotNull(restartedService.inspect(ServerTlsStore.KEY, "retained-ca", false))
        assertNull(restartedService.inspect(ServerTlsStore.KEY, "removed-ca", false))
    }

    private fun service(): ManagedTlsMaterialService {
        val files = ManagedTlsStoreFiles(temporaryDirectory)
        val audit = TlsMutationAudit()
        return ManagedTlsMaterialService(files, audit, ManagedTlsCertificateOperations(files, audit))
    }
}
