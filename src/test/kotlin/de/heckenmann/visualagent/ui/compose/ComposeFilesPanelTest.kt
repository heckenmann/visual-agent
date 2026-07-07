@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.canvas.CanvasSnapshot
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeFilesPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `files panel renders workspace status and file list`() {
        val workspace = mockWorkspaceService()
        val canvas = mockk<CanvasOperations>()
        every { canvas.snapshot() } returns CanvasSnapshot(figureCount = 0, zoomPercent = 100, gridVisible = true, figures = emptyList())

        composeTestRule.setContent {
            MaterialTheme {
                FilesPanel(workspace, canvas, ComposeModalRequester { })
            }
        }

        composeTestRule.onNodeWithText("Total 2 · showing 2").assertExists()
        composeTestRule.onNodeWithText("data/notes.txt").assertExists()
    }

    @Test
    fun `search input filters visible files`() {
        val workspace = mockWorkspaceService()
        val canvas = mockk<CanvasOperations>()
        every { canvas.snapshot() } returns CanvasSnapshot(figureCount = 0, zoomPercent = 100, gridVisible = true, figures = emptyList())

        composeTestRule.setContent {
            MaterialTheme {
                FilesPanel(workspace, canvas, ComposeModalRequester { })
            }
        }

        composeTestRule.onNodeWithText("Search files").performTextInput("diagram")
        composeTestRule.onNodeWithText("Total 2 · showing 1").assertExists()
        composeTestRule.onNodeWithText("data/diagram.canvas").assertExists()
    }

    @Test
    fun `sync button refreshes status`() {
        val workspace = mockWorkspaceService()
        val canvas = mockk<CanvasOperations>()
        every { canvas.snapshot() } returns CanvasSnapshot(figureCount = 0, zoomPercent = 100, gridVisible = true, figures = emptyList())
        var synced = false
        every { workspace.syncMetadataWithFilesystem() } answers {
            synced = true
            de.heckenmann.visualagent.workspace
                .WorkspaceSyncResult(added = 1, updated = 0, removed = 0, total = 3)
        }

        composeTestRule.setContent {
            MaterialTheme {
                FilesPanel(workspace, canvas, ComposeModalRequester { })
            }
        }

        composeTestRule.onNodeWithContentDescription("Sync workspace files").performClick()
        assertTrue(synced)
        composeTestRule.onNodeWithText("Sync added=1 updated=0 removed=0").assertExists()
    }

    @Test
    fun `filterWorkspaceFiles matches query in path name and sha256`() {
        val files = sampleFiles()

        val result = filterWorkspaceFiles(files, "def", ALL_FILE_TYPES)

        assertEquals(listOf("data/diagram.canvas"), result.map { it.relativePath })
    }

    @Test
    fun `filterWorkspaceFiles applies canvas type filter`() {
        val files = sampleFiles()

        val result = filterWorkspaceFiles(files, "", CANVAS_FILE_TYPE)

        assertEquals(listOf("data/diagram.canvas"), result.map { it.relativePath })
    }

    @Test
    fun `filterWorkspaceFiles applies other type filter`() {
        val files = sampleFiles()

        val result = filterWorkspaceFiles(files, "", OTHER_FILE_TYPE)

        assertEquals(listOf("data/notes.txt"), result.map { it.relativePath })
    }

    @Test
    fun `filterWorkspaceFiles combines query and type filter`() {
        val files = sampleFiles()

        val result = filterWorkspaceFiles(files, "data", CANVAS_FILE_TYPE)

        assertEquals(listOf("data/diagram.canvas"), result.map { it.relativePath })
    }

    private fun sampleFiles(): List<WorkspaceFileRecord> =
        listOf(
            WorkspaceFileRecord(
                id = "f1",
                relativePath = "data/notes.txt",
                originalName = "notes.txt",
                mimeType = "text/plain",
                sizeBytes = 12,
                sha256 = "abc123",
                extractedText = null,
                importedAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
            WorkspaceFileRecord(
                id = "f2",
                relativePath = "data/diagram.canvas",
                originalName = "diagram.canvas",
                mimeType = de.heckenmann.visualagent.workspace.WorkspaceFilePaths.CANVAS_MIME_TYPE,
                sizeBytes = 256,
                sha256 = "def456",
                extractedText = null,
                importedAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

    private fun mockWorkspaceService(): WorkspaceFileService {
        val service = mockk<WorkspaceFileService>()
        every { service.listFiles() } returns sampleFiles()
        every { service.workspaceRoot() } returns
            java.nio.file.Path
                .of("/tmp/workspace")
        return service
    }
}
