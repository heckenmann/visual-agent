package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.DirectoryGrantRecord
import de.heckenmann.visualagent.knowledge.DirectoryGrantStore
import de.heckenmann.visualagent.protocol.ClientDirectoryCapabilityRegistration
import de.heckenmann.visualagent.protocol.ClientDirectoryEntry
import de.heckenmann.visualagent.protocol.ClientDirectoryFileAccess
import de.heckenmann.visualagent.protocol.ClientDirectoryMatch
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryGrantOrigin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DirectoryGrantServiceTest {
    @TempDir
    lateinit var temp: Path

    private lateinit var store: MemoryDirectoryGrantStore
    private lateinit var capabilities: ClientDirectoryCapabilityRegistry
    private lateinit var service: DirectoryGrantService

    @BeforeEach
    fun setUp() {
        store = MemoryDirectoryGrantStore()
        capabilities = ClientDirectoryCapabilityRegistry()
        service = DirectoryGrantService(store, capabilities, VisualAgentProtectedDirectoryPolicy(":memory:"))
    }

    @Test
    fun `canonical grant supports bounded relative reads and listing`() {
        Files.writeString(temp.resolve("README.md"), "hello grant")
        val grant = service.addServerGrant(temp.toString(), "Project", DirectoryAccessMode.READ_ONLY)

        assertEquals("hello grant", service.readText(grant.id, "README.md"))
        assertEquals(listOf("README.md"), service.list(grant.id, "").map { it.path })
        assertEquals(temp.toRealPath().toString(), grant.canonicalRoot)
    }

    @Test
    fun `rejects absolute traversal and symlink escape`() {
        val outside = Files.createTempDirectory("directory-grant-outside")
        Files.writeString(outside.resolve("secret.txt"), "secret")
        val grant = service.addServerGrant(temp.toString(), "Project", DirectoryAccessMode.READ_ONLY)

        assertThrows(IllegalArgumentException::class.java) { service.readText(grant.id, "../secret.txt") }
        assertThrows(IllegalArgumentException::class.java) { service.readText(grant.id, outside.resolve("secret.txt").toString()) }
        runCatching { Files.createSymbolicLink(temp.resolve("escape"), outside) }.onSuccess {
            assertThrows(IllegalArgumentException::class.java) { service.readText(grant.id, "escape/secret.txt") }
        }
        outside.toFile().deleteRecursively()
    }

    @Test
    fun `read only denies writes and revocation denies future reads`() {
        Files.writeString(temp.resolve("note.txt"), "before")
        val grant = service.addServerGrant(temp.toString(), "Project", DirectoryAccessMode.READ_ONLY)

        val denied = assertThrows(IllegalArgumentException::class.java) { service.writeText(grant.id, "note.txt", "after") }
        assertEquals("ACCESS_DENIED: directory is read-only", denied.message)
        assertTrue(service.removeGrant(grant.id))
        assertFalse(service.removeGrant(grant.id))
        assertThrows(IllegalArgumentException::class.java) { service.readText(grant.id, "note.txt") }
    }

    @Test
    fun `read write rechecks the persisted mode before mutation`() {
        val grant = service.addServerGrant(temp.toString(), "Project", DirectoryAccessMode.READ_WRITE)
        service.writeText(grant.id, "new.txt", "created")
        service.updateGrant(grant.id, "Project", DirectoryAccessMode.READ_ONLY)

        assertEquals("created", Files.readString(temp.resolve("new.txt")))
        assertThrows(IllegalArgumentException::class.java) { service.writeText(grant.id, "new.txt", "changed") }
    }

    @Test
    fun `server grant supports bounded bytes search and glob`() {
        Files.writeString(temp.resolve("notes.txt"), "Needle in a text file")
        Files.writeString(temp.resolve("other.md"), "No match")
        val grant = service.addServerGrant(temp.toString(), "Project", DirectoryAccessMode.READ_ONLY)

        assertEquals("Needle in a text file", service.readBytes(grant.id, "notes.txt", 100).decodeToString())
        assertEquals("notes.txt", service.glob(grant.id, "", "*.txt").single().path)
        val matches = service.search(grant.id, "needle")
        assertEquals(1, matches.size)
        assertEquals("notes.txt", matches.single().path)
        assertThrows(IllegalArgumentException::class.java) { service.readBytes(grant.id, "notes.txt", 5) }
    }

    @Test
    fun `copy and move transfer files across server grant ids`() {
        val sourceDirectory = temp.resolve("source")
        val targetDirectory = temp.resolve("target")
        Files.createDirectories(sourceDirectory)
        Files.createDirectories(targetDirectory)
        Files.writeString(sourceDirectory.resolve("report.md"), "copy me")
        Files.writeString(sourceDirectory.resolve("draft.md"), "move me")
        val readOnlySource = service.addServerGrant(sourceDirectory.toString(), "Source", DirectoryAccessMode.READ_ONLY)
        val readWriteSource = service.addServerGrant(temp.toString(), "Parent", DirectoryAccessMode.READ_WRITE)
        val target = service.addServerGrant(targetDirectory.toString(), "Target", DirectoryAccessMode.READ_WRITE)

        assertEquals("report-copy.md", service.copy(readOnlySource.id, "report.md", target.id, "report-copy.md"))
        assertEquals("copy me", Files.readString(targetDirectory.resolve("report-copy.md")))
        assertEquals("draft-moved.md", service.move(readWriteSource.id, "source/draft.md", target.id, "draft-moved.md"))
        assertEquals("move me", Files.readString(targetDirectory.resolve("draft-moved.md")))
        assertFalse(Files.exists(sourceDirectory.resolve("draft.md")))
    }

    @Test
    fun `client grant delegates only to its registered owner and never persists a local root`() {
        val registration = ClientDirectoryCapabilityRegistration("grant-client", "desktop-a", "capability-a")
        val access = FakeClientDirectoryAccess()
        capabilities.register(registration, access)

        val grant = service.addClientGrant(registration, "Local documents", DirectoryAccessMode.READ_ONLY)

        assertEquals(DirectoryGrantOrigin.CLIENT, grant.origin)
        assertNull(grant.canonicalRoot)
        assertEquals("desktop-a", grant.ownerClientId)
        assertEquals("client content", service.readText(grant.id, "notes.txt"))
        assertEquals(listOf("notes.txt"), service.list(grant.id, "").map { it.path })
        assertThrows(IllegalArgumentException::class.java) { service.writeText(grant.id, "notes.txt", "changed") }
        assertFalse(access.writeCalled)
    }

    @Test
    fun `client grant cannot be rebound by another client and is revoked immediately`() {
        val registration = ClientDirectoryCapabilityRegistration("grant-client", "desktop-a", "capability-a")
        capabilities.register(registration, FakeClientDirectoryAccess())
        val grant = service.addClientGrant(registration, "Local documents", DirectoryAccessMode.READ_WRITE)

        assertThrows(IllegalArgumentException::class.java) {
            capabilities.register(
                ClientDirectoryCapabilityRegistration(grant.id, "desktop-b", "capability-b"),
                FakeClientDirectoryAccess(),
            )
        }

        assertTrue(service.removeGrant(grant.id))
        assertFalse(capabilities.isAvailable(registration))
        assertThrows(IllegalArgumentException::class.java) { service.readText(grant.id, "notes.txt") }
    }

    @Test
    fun `configuration and data roots cannot be granted directly or through a parent`() {
        val protectedData = temp.resolve("data")
        Files.createDirectories(protectedData)
        val protectedPolicy = VisualAgentProtectedDirectoryPolicy(protectedData.resolve("visual-agent.db").toString())
        val protectedService = DirectoryGrantService(store, capabilities, protectedPolicy)
        val sibling = temp.resolve("project")
        Files.createDirectory(sibling)

        assertThrows(IllegalArgumentException::class.java) {
            protectedService.addServerGrant(protectedData.toString(), "Data", DirectoryAccessMode.READ_ONLY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            protectedService.addServerGrant(temp.toString(), "Parent", DirectoryAccessMode.READ_ONLY)
        }
        assertEquals("Project", protectedService.addServerGrant(sibling.toString(), "Project", DirectoryAccessMode.READ_ONLY).displayName)
    }
}

private class FakeClientDirectoryAccess : ClientDirectoryFileAccess {
    var writeCalled = false

    override fun isAvailable(): Boolean = true

    override fun list(relativePath: String): List<ClientDirectoryEntry> =
        listOf(ClientDirectoryEntry("notes.txt", directory = false, sizeBytes = 14))

    override fun readText(relativePath: String): String = "client content"

    override fun readBytes(
        relativePath: String,
        maximumBytes: Long,
    ): ByteArray = "client content".encodeToByteArray()

    override fun search(
        query: String,
        path: String,
    ): List<ClientDirectoryMatch> = emptyList()

    override fun glob(
        path: String,
        pattern: String,
    ): List<ClientDirectoryEntry> = emptyList()

    override fun writeText(
        relativePath: String,
        content: String,
    ): String {
        writeCalled = true
        return relativePath
    }

    override fun createDirectory(relativePath: String): String = relativePath

    override fun delete(
        relativePath: String,
        recursive: Boolean,
    ) = Unit
}

private class MemoryDirectoryGrantStore : DirectoryGrantStore {
    private val records = linkedMapOf<String, DirectoryGrantRecord>()

    override fun saveDirectoryGrant(record: DirectoryGrantRecord) {
        records[record.id] = record
    }

    override fun listDirectoryGrants(): List<DirectoryGrantRecord> = records.values.toList()

    override fun getDirectoryGrant(id: String): DirectoryGrantRecord? = records[id]

    override fun getDirectoryGrantByCanonicalRoot(canonicalRoot: String): DirectoryGrantRecord? =
        records.values.firstOrNull { it.canonicalRoot == canonicalRoot }

    override fun deleteDirectoryGrant(id: String): Boolean = records.remove(id) != null
}
