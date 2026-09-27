package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerTlsToolsTest {
    @Test
    fun `trust tool imports certificate and reports restart requirement`() {
        val material = FakeServerTlsMaterial()
        val tool = ServerTrustStoreTool(material)

        val result = tool.execute("""{"action":"importCertificate","alias":"company-ca","certificate":"pem"}""", emptyMap())

        assertTrue(result.success)
        assertEquals("company-ca", material.importedAlias)
        assertEquals("pem", material.importedCertificate)
        assertTrue(result.content.contains("\"restartRequired\":true"))
    }

    @Test
    fun `key tool signs generated server certificate with selected CA and never returns private key`() {
        val material = FakeServerTlsMaterial()
        val tool = ServerKeyStoreTool(material)

        val result =
            tool.execute(
                """{"action":"generateCertificate","alias":"service","subject":"CN=service.example","dnsNames":["service.example"],"certificateAuthority":false,"signingCaAlias":"company-root"}""",
                emptyMap(),
            )

        assertTrue(result.success)
        assertEquals("company-root", material.signingCaAlias)
        assertTrue(result.content.contains("\"restartRequired\":true"))
        assertFalse(result.content.contains("PRIVATE KEY"))
        assertTrue(result.content.contains("service.example"))
    }

    private class FakeServerTlsMaterial : ServerTlsMaterialPort {
        var importedAlias: String? = null
        var importedCertificate: String? = null
        var signingCaAlias: String? = null

        override fun list(store: ServerTlsStore): List<ServerTlsEntry> = emptyList()

        override fun inspect(
            store: ServerTlsStore,
            alias: String,
            includePem: Boolean,
        ): ServerTlsEntry? = null

        override fun importTrustedCertificate(
            alias: String,
            certificate: String,
        ): ServerTlsEntry {
            importedAlias = alias
            importedCertificate = certificate
            return ServerTlsEntry(alias, "trusted_certificate", emptyList())
        }

        override fun removeTrustedCertificate(alias: String): Boolean = false

        override fun generateCertificate(
            alias: String,
            subject: String,
            dnsNames: List<String>,
            ipAddresses: List<String>,
            certificateAuthority: Boolean,
            validityDays: Int,
            signingCaAlias: String?,
        ): GeneratedServerCertificate {
            this.signingCaAlias = signingCaAlias
            return GeneratedServerCertificate(alias, certificateInfo, certificateAuthority, true)
        }

        override fun removeKeyEntry(alias: String): Boolean = false

        override fun exportPublicCertificate(alias: String): String? = null
    }

    private companion object {
        val certificateInfo =
            ServerCertificateInfo(
                "CN=service.example",
                "CN=company-root",
                "abc123",
                "2026-01-01T00:00:00Z",
                "2027-01-01T00:00:00Z",
                -1,
                listOf("dns:service.example"),
            )
    }
}
