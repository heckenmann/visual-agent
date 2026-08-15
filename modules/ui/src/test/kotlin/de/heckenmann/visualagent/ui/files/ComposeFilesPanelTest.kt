package de.heckenmann.visualagent.ui.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.CANVAS_MIME_TYPE
import de.heckenmann.visualagent.protocol.CanvasPort
import de.heckenmann.visualagent.protocol.WorkspaceFile
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies that the files panel consumes only protocol-owned workspace values. */
class ComposeFilesPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `filter matches metadata and type`() {
        val files = sampleFiles()
        assertEquals(listOf("data/diagram.canvas"), filterWorkspaceFiles(files, "def", ALL_FILE_TYPES).map { it.relativePath })
        assertEquals(listOf("data/diagram.canvas"), filterWorkspaceFiles(files, "", CANVAS_FILE_TYPE).map { it.relativePath })
        assertEquals(listOf("data/notes.txt"), filterWorkspaceFiles(files, "", OTHER_FILE_TYPE).map { it.relativePath })
    }

    @Test
    fun `panel renders protocol workspace values`() {
        val workspace = mockk<WorkspaceFilePort>()
        every { workspace.listFiles() } returns sampleFiles()
        every { workspace.workspaceRoot() } returns "/tmp/workspace"
        val canvas = mockk<CanvasPort>(relaxed = true)
        val activity = mockk<ActivityPort>(relaxed = true)
        composeTestRule.setContent {
            MaterialTheme {
                FilesPanel(workspace, canvas, ComposeModalRequester { }, activity)
            }
        }
        composeTestRule.onNodeWithText("Total 2 · showing 2").assertExists()
        composeTestRule.onNodeWithText("data/notes.txt").assertExists()
    }

    private fun sampleFiles() =
        listOf(
            WorkspaceFile("f1", "data/notes.txt", "notes.txt", "text/plain", 12, "abc123", "now", "now"),
            WorkspaceFile("f2", "data/diagram.canvas", "diagram.canvas", CANVAS_MIME_TYPE, 256, "def456", "now", "now"),
        )
}
