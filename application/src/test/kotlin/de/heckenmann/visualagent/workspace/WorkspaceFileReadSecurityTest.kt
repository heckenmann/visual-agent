package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkspaceFileReadSecurityTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun `all managed content paths reject a file replaced by an external symlink`() {
        val service = service()
        val record = service.createManagedFile("imports", "fixture.txt", "safe".toByteArray(), "text/plain")
        val outside = Files.writeString(temp.resolve("outside.txt"), "EXTERNAL_FIXTURE_ONLY")
        replaceWithLink(service.workspaceRoot().resolve(record.relativePath), outside)

        assertAllReadsRejected(service, record)
        assertEquals("EXTERNAL_FIXTURE_ONLY", Files.readString(outside))
    }

    @Test
    fun `all managed content paths reject a replaced ancestor directory`() {
        val service = service()
        val record = service.createManagedFile("imports", "fixture.txt", "safe".toByteArray(), "text/plain")
        val imports = service.workspaceRoot().resolve("imports")
        Files.move(imports, service.workspaceRoot().resolve("original-imports"))
        val outside = Files.createDirectory(temp.resolve("outside"))
        Files.writeString(outside.resolve("fixture.txt"), "EXTERNAL_FIXTURE_ONLY")
        createLink(imports, outside)

        assertAllReadsRejected(service, record)
    }

    @Test
    fun `in-workspace links are rejected consistently and ordinary files still work`() {
        val service = service()
        val record = service.createManagedFile("imports", "fixture.txt", "safe".toByteArray(), "text/plain")
        assertEquals("safe", service.readText(record))
        assertEquals(record.sha256, service.hash(record))
        val path = service.workspaceRoot().resolve(record.relativePath)
        val target = Files.move(path, path.resolveSibling("original.txt"))
        createLink(path, target)

        assertAllReadsRejected(service, record)
    }

    @Test
    fun `replaced workspace root is rejected instead of redefining the authorized area`() {
        val service = service()
        val root = service.workspaceRoot()
        Files.move(root, root.resolveSibling("original-workspace"))
        val outside = Files.createDirectory(temp.resolve("outside-root"))
        createLink(root, outside)

        assertFailsWith<IllegalArgumentException> { service.workspaceRoot() }
    }

    private fun assertAllReadsRejected(
        service: WorkspaceFileService,
        record: WorkspaceFileRecord,
    ) {
        val reads: List<() -> Any> =
            listOf(
                { service.resolveManagedPath(record.relativePath) },
                { service.readText(record) },
                { service.hash(record) },
                { service.readBytes(record, 1024) },
                { service.imageInfo(record) },
                { service.imageBytes(record) },
                { service.detectMimeType(record) },
                { service.extractPdfText(record) },
                { service.extractPdfText(record.copy(extractedText = "cached")) },
                { service.renderPdfPage(record, 1) },
            )
        reads.forEach { read -> assertFailsWith<IllegalArgumentException> { read() } }
    }

    private fun service() = WorkspaceFileService(FakeWorkspaceFileStore(), temp.resolve("data/agent.db").toString())

    private fun replaceWithLink(
        path: Path,
        target: Path,
    ) {
        Files.delete(path)
        createLink(path, target)
    }

    private fun createLink(
        path: Path,
        target: Path,
    ) {
        val result = runCatching { Files.createSymbolicLink(path, target) }
        assumeTrue(result.isSuccess, "Symbolic links are unavailable on this test filesystem")
    }
}
