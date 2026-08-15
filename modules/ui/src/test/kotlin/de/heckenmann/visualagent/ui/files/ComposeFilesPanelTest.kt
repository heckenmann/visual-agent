package de.heckenmann.visualagent.ui.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import java.nio.file.Files
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

    @Test
    fun `typed file path is imported asynchronously`() {
        val source = Files.createTempFile("visual-agent", ".txt").toFile().apply { writeText("hello") }
        var imported = false
        val workspace = mockk<WorkspaceFilePort>(relaxed = true)
        every { workspace.listFiles() } returns emptyList()
        every { workspace.workspaceRoot() } returns "/tmp/workspace"
        every { workspace.importFile(source.name, any()) } answers {
            imported = true
            WorkspaceFile("f1", "data/${source.name}", source.name, "text/plain", 5, "hash", "now", "now")
        }
        val canvas = mockk<CanvasPort>(relaxed = true)
        val activity = mockk<ActivityPort>(relaxed = true)
        composeTestRule.setContent {
            MaterialTheme {
                FilesPanel(workspace, canvas, ComposeModalRequester { }, activity)
            }
        }

        composeTestRule.onNodeWithText("Import path").performTextInput(source.absolutePath)
        composeTestRule.onNodeWithContentDescription("Import typed path").performClick()
        composeTestRule.waitUntil(5_000) { imported }
        assertEquals(true, imported)
        source.delete()
    }

    private fun sampleFiles() =
        listOf(
            WorkspaceFile("f1", "data/notes.txt", "notes.txt", "text/plain", 12, "abc123", "now", "now"),
            WorkspaceFile("f2", "data/diagram.canvas", "diagram.canvas", CANVAS_MIME_TYPE, 256, "def456", "now", "now"),
        )
}
