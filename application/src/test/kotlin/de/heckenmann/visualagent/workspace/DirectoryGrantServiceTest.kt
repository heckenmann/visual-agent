package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.DirectoryGrantRecord
import de.heckenmann.visualagent.knowledge.DirectoryGrantStore
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DirectoryGrantServiceTest {
    @TempDir
    lateinit var temp: Path

    private val store = MemoryDirectoryGrantStore()
    private val service = DirectoryGrantService(store)

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
