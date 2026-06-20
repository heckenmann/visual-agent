package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnowledgeDbWorkspaceFileTest {
    @Test
    fun `workspace file store saves lists finds and deletes metadata`() {
        val dbFile = Files.createTempFile("visual-agent-workspace", ".db")
        KnowledgeDbTestFactory.create(dbFile.toString()).use { db ->
            val record =
                WorkspaceFileRecord(
                    id = "file-1",
                    relativePath = "imports/a.txt",
                    originalName = "a.txt",
                    mimeType = "text/plain",
                    sizeBytes = 5,
                    sha256 = "a".repeat(64),
                    extractedText = "Alpha",
                    importedAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                )

            db.saveWorkspaceFile(record)

            assertEquals(record, db.getWorkspaceFile("file-1"))
            assertEquals(record, db.getWorkspaceFileByPath("imports/a.txt"))
            assertEquals(listOf(record), db.listWorkspaceFiles())
            assertTrue(db.deleteWorkspaceFile("file-1"))
            assertFalse(db.deleteWorkspaceFile("file-1"))
            assertNotNull(db)
        }
    }
}
