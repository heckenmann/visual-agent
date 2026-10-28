package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.error.CanvasOperationException
import de.heckenmann.visualagent.image.RgbaPngEncoder
import de.heckenmann.visualagent.workspace.FakeWorkspaceFileStore
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanvasCaptureServiceTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun `capture uses managed image bytes and survives document reload`() {
        val workspace = workspace()
        val fixture = RgbaPngEncoder.encodeArgb(2, 2, IntArray(4) { RED })
        val record = workspace.createManagedFile("imports", "fixture.png", fixture, "image/png")
        val service = InMemoryCanvasService(workspace)
        service.insertImage(workspace.resolveManagedPath(record.relativePath).toString())

        val original = service.captureImage("png")
        val reloaded = InMemoryCanvasService(workspace).captureImage("png")
        assertContentEquals(original.bytes, reloaded.bytes)
        Image.makeFromEncoded(reloaded.bytes).use { image ->
            Bitmap.makeFromImage(image).use { assertEquals(RED, it.getColor(160, 120)) }
        }
    }

    @Test
    fun `capture rejects external image paths without reading them`() {
        val service = InMemoryCanvasService(workspace())
        val external = Files.write(temp.resolve("external.png"), RgbaPngEncoder.encodeArgb(1, 1, intArrayOf(RED)))
        service.insertImage(external.toString())

        assertFailsWith<CanvasOperationException> { service.captureImage("png") }
    }

    @Test
    fun `oversized capture fails safely and leaves infinite editing coordinates unchanged`() {
        val service = InMemoryCanvasService(workspace())
        val expected = service.drawRect(100_000.0, 100_000.0, 10.0, 10.0, "#FFFFFF", null)

        assertFailsWith<CanvasOperationException> { service.captureImage("png") }
        assertEquals(expected, service.snapshot())
        assertEquals(expected.figures, InMemoryCanvasService(service.workspaceFileService).snapshot().figures)
    }

    private fun workspace() = WorkspaceFileService(FakeWorkspaceFileStore(), temp.resolve("data/agent.db").toString())

    private companion object {
        const val RED = 0xFFFF0000.toInt()
    }
}
