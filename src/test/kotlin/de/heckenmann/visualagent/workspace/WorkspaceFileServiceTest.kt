package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import de.heckenmann.visualagent.testsupport.TestPng
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkspaceFileServiceTest {
    @Test
    fun `workspace root is derived from database directory`() =
        withDatabasePath("jdbc:sqlite:${tempDir().resolve("db/visual-agent.db")}") { dbPath ->
            val service = WorkspaceFileService(FakeWorkspaceFileStore(), dbPath)

            assertTrue(service.workspaceRoot().endsWith("db/workspace"))
        }

    @Test
    fun `import stores metadata hash duplicate names rename and delete`() =
        withDatabasePath(tempDir().resolve("data/visual-agent.db").toString()) { dbPath ->
            val store = FakeWorkspaceFileStore()
            val service = WorkspaceFileService(store, dbPath)
            val source = tempDir().resolve("notes.txt")
            Files.writeString(source, "Alpha")

            val first = service.importFile(source.toFile())
            val second = service.importFile(source.toFile())
            val renamed = service.renameFile(first.id, "renamed")

            assertEquals("imports/notes.txt", first.relativePath)
            assertEquals("imports/notes-1.txt", second.relativePath)
            assertNotEquals(first.id, second.id)
            assertEquals("imports/renamed.txt", renamed.relativePath)
            assertEquals("Alpha", service.resolveManagedPath(renamed.relativePath).readText())
            assertTrue(service.hash(renamed).matches(Regex("[a-f0-9]{64}")))
            assertTrue(service.deleteFile(renamed.id))
        }

    @Test
    fun `pdf text extraction and page preview rendering create generated workspace image`() =
        withDatabasePath(tempDir().resolve("data/visual-agent.db").toString()) { dbPath ->
            val service = WorkspaceFileService(FakeWorkspaceFileStore(), dbPath)
            val pdf = tempDir().resolve("sample.pdf")
            writePdf(pdf, "Hello PDF")
            val imported = service.importFile(pdf.toFile())

            val text = service.extractPdfText(imported)
            val preview = service.renderPdfPage(imported, 1)
            val info = service.imageInfo(preview)

            assertTrue(text.text.contains("Hello PDF"))
            assertEquals("generated/sample-page-1.png", preview.relativePath)
            assertEquals("image/png", preview.mimeType)
            assertEquals(1200, info.width)
            assertEquals(1600, info.height)
            assertTrue(service.resolveManagedPath(preview.relativePath).isRegularFile())
        }

    @Test
    fun `image metadata and base64 are available`() =
        withDatabasePath(tempDir().resolve("data/visual-agent.db").toString()) { dbPath ->
            val service = WorkspaceFileService(FakeWorkspaceFileStore(), dbPath)
            val imagePath = tempDir().resolve("pixel.png")
            TestPng.write(imagePath, 2, 3)
            val imported = service.importFile(imagePath.toFile())

            val info = service.imageInfo(imported)
            val bytes = service.imageBytes(imported)

            assertEquals(2, info.width)
            assertEquals(3, info.height)
            assertEquals("image/png", bytes.mimeType)
            assertTrue(bytes.base64.isNotBlank())
        }

    @Test
    fun `search matches metadata and text content`() =
        withDatabasePath(tempDir().resolve("data/visual-agent.db").toString()) { dbPath ->
            val service = WorkspaceFileService(FakeWorkspaceFileStore(), dbPath)
            val textFile = tempDir().resolve("notes.txt")
            Files.writeString(textFile, "Project Falcon is ready")
            val imported = service.importFile(textFile.toFile())

            val metadata = service.searchFiles(imported.sha256.take(12))
            val content = service.searchFiles("falcon")

            assertEquals(
                imported.id,
                metadata
                    .matches
                    .single()
                    .record
                    .id,
            )
            assertEquals(
                "metadata",
                metadata
                    .matches
                    .single()
                    .matchType,
            )
            assertEquals(
                imported.id,
                content
                    .matches
                    .single()
                    .record
                    .id,
            )
            assertEquals(
                "content",
                content
                    .matches
                    .single()
                    .matchType,
            )
        }

    @Test
    fun `sync reconciles missing changed and unmanaged workspace files`() =
        withDatabasePath(tempDir().resolve("data/visual-agent.db").toString()) { dbPath ->
            val store = FakeWorkspaceFileStore()
            val service = WorkspaceFileService(store, dbPath)
            val created =
                service.createManagedFile(
                    "canvas",
                    "diagram.draw",
                    "<drawing/>".toByteArray(),
                    "application/vnd.visual-agent.canvas+xml",
                )
            val removed = service.createManagedFile("imports", "removed.txt", "gone".toByteArray(), "text/plain")
            service.resolveManagedPath(created.relativePath).writeText("<drawing>changed</drawing>")
            service.resolveManagedPath(removed.relativePath).deleteIfExists()
            val unmanaged = service.workspaceRoot().resolve("imports/unmanaged.txt")
            unmanaged.parent.createDirectories()
            unmanaged.writeText("new")

            val report = service.syncMetadataWithFilesystem()

            assertEquals(1, report.added)
            assertEquals(1, report.updated)
            assertEquals(1, report.removed)
            assertTrue(service.listFiles().any { it.relativePath == "imports/unmanaged.txt" })
            assertEquals("<drawing>changed</drawing>", service.readText(service.requireFile(created.id, null)))
        }

    private fun withDatabasePath(
        path: String,
        block: (String) -> Unit,
    ) {
        block(path)
    }

    private fun tempDir(): Path = Files.createTempDirectory("visual-agent-workspace-test")

    private fun writePdf(
        path: Path,
        text: String,
    ) {
        path.parent?.createDirectories()
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                stream.newLineAtOffset(72f, 720f)
                stream.showText(text)
                stream.endText()
            }
            document.save(path.toFile())
        }
    }

    private class FakeWorkspaceFileStore : WorkspaceFileStore {
        private val records = linkedMapOf<String, WorkspaceFileRecord>()

        override fun saveWorkspaceFile(record: WorkspaceFileRecord) {
            records[record.id] = record
        }

        override fun listWorkspaceFiles(): List<WorkspaceFileRecord> = records.values.sortedByDescending(WorkspaceFileRecord::importedAt)

        override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = records[id]

        override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? =
            records.values.firstOrNull { it.relativePath == relativePath }

        override fun deleteWorkspaceFile(id: String): Boolean = records.remove(id) != null
    }
}
