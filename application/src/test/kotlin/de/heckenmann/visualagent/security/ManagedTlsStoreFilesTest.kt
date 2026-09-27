package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ManagedTlsStoreFilesTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `rejects symbolic link store file pointing outside managed tls directory`() {
        val tlsDirectory = temporaryDirectory.resolve("security/tls")
        Files.createDirectories(tlsDirectory)
        val outsideFile = temporaryDirectory.resolve("external-store.p12")
        Files.writeString(outsideFile, "external-store-content")
        val link = tlsDirectory.resolve("keystore.p12")
        assumeTrue(createLink(link, outsideFile), "Symbolic links are unavailable on this filesystem")

        assertFailsWith<IOException> { ManagedTlsStoreFiles(temporaryDirectory).load(ServerTlsStore.KEY) }
        assertEquals("external-store-content", Files.readString(outsideFile))
    }

    @Test
    fun `rejects symbolic link password file pointing outside managed tls directory`() {
        val tlsDirectory = temporaryDirectory.resolve("security/tls")
        Files.createDirectories(tlsDirectory)
        val outsideFile = temporaryDirectory.resolve("external-password")
        Files.writeString(outsideFile, "must-not-be-used")
        val link = tlsDirectory.resolve(".keystore-password")
        assumeTrue(createLink(link, outsideFile), "Symbolic links are unavailable on this filesystem")

        assertFailsWith<IOException> { ManagedTlsStoreFiles(temporaryDirectory).passwordChars(ServerTlsStore.KEY) }
        assertEquals("must-not-be-used", Files.readString(outsideFile))
    }

    @Test
    fun `rejects an empty managed password file instead of opening an empty-password store`() {
        val tlsDirectory = temporaryDirectory.resolve("security/tls")
        Files.createDirectories(tlsDirectory)
        Files.writeString(tlsDirectory.resolve(".keystore-password"), "")

        assertFailsWith<IOException> { ManagedTlsStoreFiles(temporaryDirectory).load(ServerTlsStore.KEY) }
        assertEquals(false, Files.exists(tlsDirectory.resolve("keystore.p12")))
    }

    @Test
    fun `missing password does not replace credentials for an existing store`() {
        val files = ManagedTlsStoreFiles(temporaryDirectory)
        files.load(ServerTlsStore.KEY)
        val tlsDirectory = temporaryDirectory.resolve("security/tls")
        val passwordFile = tlsDirectory.resolve(".keystore-password")
        val storeFile = tlsDirectory.resolve("keystore.p12")
        val originalStoreBytes = Files.readAllBytes(storeFile)
        Files.delete(passwordFile)

        assertFailsWith<IOException> { files.load(ServerTlsStore.KEY) }
        assertEquals(false, Files.exists(passwordFile))
        assertEquals(originalStoreBytes.toList(), Files.readAllBytes(storeFile).toList())
    }

    @Test
    fun `rejects symbolic link tls directory before creating managed files`() {
        val securityDirectory = temporaryDirectory.resolve("security")
        Files.createDirectories(securityDirectory)
        val outsideDirectory = temporaryDirectory.resolve("external-tls")
        Files.createDirectories(outsideDirectory)
        val link = securityDirectory.resolve("tls")
        assumeTrue(createLink(link, outsideDirectory), "Symbolic links are unavailable on this filesystem")

        assertFailsWith<IOException> { ManagedTlsStoreFiles(temporaryDirectory).load(ServerTlsStore.KEY) }
        assertEquals(emptyList(), Files.list(outsideDirectory).use { it.toList() })
    }

    @Test
    fun `rejects symbolic link backup without reading or overwriting its target`() {
        val tlsDirectory = temporaryDirectory.resolve("security/tls")
        Files.createDirectories(tlsDirectory)
        val outsideFile = temporaryDirectory.resolve("external-backup.p12")
        Files.writeString(outsideFile, "external-backup-content")
        val link = tlsDirectory.resolve("keystore.p12.backup")
        assumeTrue(createLink(link, outsideFile), "Symbolic links are unavailable on this filesystem")

        assertFailsWith<IOException> { ManagedTlsStoreFiles(temporaryDirectory).load(ServerTlsStore.KEY) }
        assertEquals("external-backup-content", Files.readString(outsideFile))
    }

    @Test
    fun `restricts tls directory and private files on posix filesystems`() {
        val files = ManagedTlsStoreFiles(temporaryDirectory)
        val audit = TlsMutationAudit()
        val service = ManagedTlsMaterialService(files, audit, ManagedTlsCertificateOperations(files, audit))
        service.generateCertificate("root-ca", "CN=Test Root CA", emptyList(), emptyList(), true, 30)
        val tlsDirectory = temporaryDirectory.resolve("security/tls")
        assumeTrue(
            Files.getFileAttributeView(tlsDirectory, PosixFileAttributeView::class.java) != null,
            "POSIX file permissions are unavailable on this filesystem",
        )

        assertEquals(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE), Files.getPosixFilePermissions(tlsDirectory))
        listOf("keystore.p12", "keystore.p12.backup", ".keystore-password").forEach { name ->
            assertEquals(setOf(OWNER_READ, OWNER_WRITE), Files.getPosixFilePermissions(tlsDirectory.resolve(name)))
        }
    }

    private fun createLink(
        link: Path,
        target: Path,
    ): Boolean = runCatching { Files.createSymbolicLink(link, target) }.isSuccess
}
