package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import de.heckenmann.visualagent.workspace.WorkspaceFilePaths
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InMemoryCanvasServiceTest {
    @Test
    fun `draw operations append figure snapshots in draw order`() =
        withCanvasService { service, _ ->
            service.drawText("Hello", x = 10.0, y = 20.0, color = "#fff")
            service.drawRect(x = 30.0, y = 40.0, width = 120.0, height = 80.0, fillColor = "#000", strokeColor = null)
            service.drawLine(x1 = 5.0, y1 = 8.0, x2 = 25.0, y2 = 18.0, color = "#f00", width = 3.0)
            service.drawCircle(centerX = 100.0, centerY = 90.0, radius = 12.0, fillColor = "#0f0")
            service.insertImage("/tmp/example.png")

            val snapshot = service.snapshot()

            assertEquals(5, snapshot.figureCount)
            assertEquals(listOf("text", "rectangle", "line", "circle", "image"), snapshot.figures.map { it.type })
            assertEquals(listOf(0, 1, 2, 3, 4), snapshot.figures.map { it.index })
            assertEquals(setOf(4), snapshot.selectedFigureIndices)
            assertEquals(88.0, snapshot.figures[3].x)
            assertEquals(24.0, snapshot.figures[3].width)
        }

    @Test
    fun `clear removes all figures and keeps view state stable`() =
        withCanvasService { service, _ ->
            service.drawText("Hello", x = 1.0, y = 2.0, color = "#fff")

            val snapshot = service.clear()

            assertEquals(0, snapshot.figureCount)
            assertEquals(100, snapshot.zoomPercent)
            assertTrue(snapshot.gridVisible)
            assertEquals(emptySet<Int>(), snapshot.selectedFigureIndices)
        }

    @Test
    fun `selection move resize and delete update one figure`() =
        withCanvasService { service, _ ->
            service.drawRect(x = 30.0, y = 40.0, width = 120.0, height = 80.0, fillColor = "#000", strokeColor = null)
            service.drawCircle(centerX = 220.0, centerY = 100.0, radius = 20.0, fillColor = "#0f0")

            val selected = service.selectAt(35.0, 45.0)
            val moved = service.moveFigure(0, deltaX = 10.0, deltaY = 12.0)
            val resized = service.resizeFigure(0, width = 90.0, height = 70.0)
            val deleted = service.deleteSelectedFigures()

            assertEquals(setOf(0), selected.selectedFigureIndices)
            assertEquals(40.0, moved.figures[0].x)
            assertEquals(52.0, moved.figures[0].y)
            assertEquals(90.0, resized.figures[0].width)
            assertEquals(70.0, resized.figures[0].height)
            assertEquals(1, deleted.figureCount)
            assertEquals(0, deleted.figures.single().index)
            assertEquals("circle", deleted.figures.single().type)
        }

    @Test
    fun `multi-selection deletes all selected figures`() =
        withCanvasService { service, _ ->
            service.drawRect(x = 30.0, y = 40.0, width = 120.0, height = 80.0, fillColor = "#000", strokeColor = null)
            service.drawCircle(centerX = 220.0, centerY = 100.0, radius = 20.0, fillColor = "#0f0")
            service.drawText("Hello", x = 10.0, y = 20.0, color = "#fff")

            service.selectFigures(setOf(0, 2))
            val deleted = service.deleteSelectedFigures()

            assertEquals(1, deleted.figureCount)
            assertEquals("circle", deleted.figures.single().type)
            assertEquals(emptySet<Int>(), deleted.selectedFigureIndices)
        }

    @Test
    fun `capture image renders current canvas as png`() =
        withCanvasService { service, _ ->
            service.drawRect(x = 30.0, y = 40.0, width = 120.0, height = 80.0, fillColor = "#000", strokeColor = null)

            val image = service.captureImage("png")

            assertEquals("png", image.format)
            assertEquals("image/png", image.mimeType)
            assertEquals(800, image.width)
            assertEquals(600, image.height)
            val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            assertTrue(
                image.bytes
                    .take(8)
                    .toByteArray()
                    .contentEquals(pngSignature),
            )
            assertTrue(image.bytes.size > 100)
        }

    @Test
    fun `canvas state is restored from default workspace document`() =
        withCanvasService { service, workspace ->
            service.drawRect(x = 30.0, y = 40.0, width = 120.0, height = 80.0, fillColor = "#000", strokeColor = null)

            val restored = InMemoryCanvasService(workspace).snapshot()

            assertEquals(1, restored.figureCount)
            assertEquals("rectangle", restored.figures.single().type)
        }

    @Test
    fun `save and open document round-trip editable canvas files`() =
        withCanvasService { service, workspace ->
            service.drawCircle(centerX = 50.0, centerY = 60.0, radius = 10.0, fillColor = "#0f0")
            val saved = service.saveDocument("diagram")
            service.clear()

            val opened = service.openDocument(saved.id, null)

            assertEquals(WorkspaceFilePaths.CANVAS_MIME_TYPE, saved.mimeType)
            assertTrue(saved.relativePath.endsWith(".canvas"))
            assertEquals(1, opened.figureCount)
            assertEquals("circle", opened.figures.single().type)
            assertEquals(opened.figureCount, InMemoryCanvasService(workspace).snapshot().figureCount)
        }

    @Test
    fun `capture rejects unsupported image formats`() =
        withCanvasService { service, _ ->
            assertFailsWith<IllegalArgumentException> {
                service.captureImage("jpg")
            }
        }

    private fun withCanvasService(block: (InMemoryCanvasService, WorkspaceFileService) -> Unit) {
        val previous = AppConfig.instance.databasePath
        try {
            AppConfig.instance.databasePath = tempDir().resolve("data/visual-agent.db").toString()
            val workspace = WorkspaceFileService(FakeWorkspaceFileStore())
            block(InMemoryCanvasService(workspace), workspace)
        } finally {
            AppConfig.instance.databasePath = previous
        }
    }

    private fun tempDir(): Path = Files.createTempDirectory("visual-agent-canvas-test")

    private class FakeWorkspaceFileStore : WorkspaceFileStore {
        private val records = linkedMapOf<String, WorkspaceFileRecord>()

        override fun saveWorkspaceFile(record: WorkspaceFileRecord) {
            records[record.id] = record
        }

        override fun listWorkspaceFiles(): List<WorkspaceFileRecord> = records.values.toList()

        override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = records[id]

        override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? =
            records.values.firstOrNull { it.relativePath == relativePath }

        override fun deleteWorkspaceFile(id: String): Boolean = records.remove(id) != null
    }
}
