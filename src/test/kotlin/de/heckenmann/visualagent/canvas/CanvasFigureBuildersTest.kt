package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.error.CanvasOperationException
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanvasFigureBuildersTest {
    @Test
    fun `withCanvasExtension keeps existing extension`() {
        assertEquals("diagram.canvas", "diagram.canvas".withCanvasExtension())
        assertEquals("DIAGRAM.CANVAS", "DIAGRAM.CANVAS".withCanvasExtension())
    }

    @Test
    fun `withCanvasExtension appends extension when missing`() {
        assertEquals("diagram.canvas", "diagram".withCanvasExtension())
    }

    @Test
    fun `distinctAdjacent removes consecutive duplicate points`() {
        val points =
            listOf(
                CanvasPoint(0.0, 0.0),
                CanvasPoint(0.0, 0.0),
                CanvasPoint(1.0, 1.0),
                CanvasPoint(1.0, 1.0),
                CanvasPoint(2.0, 2.0),
            )

        val result = points.distinctAdjacent()

        assertEquals(
            listOf(
                CanvasPoint(0.0, 0.0),
                CanvasPoint(1.0, 1.0),
                CanvasPoint(2.0, 2.0),
            ),
            result,
        )
    }

    @Test
    fun `figure contains point uses bounding box`() {
        val figure = CanvasFigureSnapshot(index = 0, type = "rectangle", x = 10.0, y = 10.0, width = 20.0, height = 20.0)

        assertEquals(true, figure.contains(15.0, 15.0))
        assertEquals(true, figure.contains(10.0, 10.0))
        assertEquals(true, figure.contains(30.0, 30.0))
        assertEquals(false, figure.contains(5.0, 15.0))
        assertEquals(false, figure.contains(15.0, 35.0))
    }

    @Test
    fun `requireFigure throws for missing index`() {
        val dbPath =
            Files
                .createTempDirectory("visual-agent-figure-test")
                .resolve("data/visual-agent.db")
                .toString()
        val service =
            InMemoryCanvasService(
                workspaceFileService =
                    WorkspaceFileService(
                        object : WorkspaceFileStore {
                            override fun saveWorkspaceFile(record: WorkspaceFileRecord) {}

                            override fun listWorkspaceFiles(): List<WorkspaceFileRecord> = emptyList()

                            override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = null

                            override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? = null

                            override fun deleteWorkspaceFile(id: String): Boolean = false
                        },
                        dbPath,
                    ),
            )

        assertFailsWith<CanvasOperationException> {
            service.requireFigure(0)
        }
    }
}
