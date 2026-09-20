package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies the directory grant columns exposed by the H2 R2DBC schema. */
class DirectoryGrantMigrationTest {
    @Test
    fun `directory grants support sync and reactive persistence operations`() {
        val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:directory-grants")
        val record =
            DirectoryGrantRecord(
                id = "grant-1",
                displayName = "Workspace",
                canonicalRoot = "/tmp/workspace",
                origin = "SERVER",
                mode = "READ_WRITE",
                ownerClientId = "client-1",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
        try {
            db.directoryGrantStore.saveDirectoryGrant(record)
            assertEquals(record, db.directoryGrantStore.getDirectoryGrant("grant-1"))
            assertEquals(record, db.directoryGrantStore.getDirectoryGrantByCanonicalRoot("/tmp/workspace"))
            assertEquals(listOf(record), db.directoryGrantStore.listDirectoryGrants())

            val updated = record.copy(ownerClientId = null, updatedAt = Instant.parse("2026-01-02T00:00:00Z"))
            db.directoryGrantStore.saveDirectoryGrantReactive(updated).block()
            assertEquals(updated, db.directoryGrantStore.getDirectoryGrantReactive("grant-1").block())
            assertTrue(db.directoryGrantStore.deleteDirectoryGrantReactive("grant-1").block() == true)
            assertFalse(db.directoryGrantStore.deleteDirectoryGrant("grant-1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `schema initializer creates the mapped directory grant owner column`() {
        val databasePath = Files.createTempDirectory("visual-agent-directory-grants").resolve("database")
        val db = KnowledgeDbTestFactory.create(databasePath.toString())

        val names =
            db.databaseClient
                .sql(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'DIRECTORY_GRANTS'",
                ).map { row, _ -> row.get("COLUMN_NAME", String::class.java) ?: error("Missing column name") }
                .all()
                .collectList()
                .block()
                .orEmpty()
                .toSet()

        assertTrue("OWNER_CLIENT_ID" in names)
        assertTrue("CLIENT_BINDING_ID" !in names)
        db.close()
    }
}
