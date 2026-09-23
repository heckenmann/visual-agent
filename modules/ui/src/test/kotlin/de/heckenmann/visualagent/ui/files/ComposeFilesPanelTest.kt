package de.heckenmann.visualagent.ui.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.CANVAS_MIME_TYPE
import de.heckenmann.visualagent.protocol.CanvasPort
import de.heckenmann.visualagent.protocol.WorkspaceDownload
import de.heckenmann.visualagent.protocol.WorkspaceDownloadState
import de.heckenmann.visualagent.protocol.WorkspaceFile
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import de.heckenmann.visualagent.protocol.WorkspaceSyncResult
import de.heckenmann.visualagent.ui.CompletionIdlingResource
import de.heckenmann.visualagent.ui.awaitCompletion
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    fun `metadata copy format retains the complete sha256`() {
        val file =
            WorkspaceFile(
                "f1",
                "data/notes.txt",
                "notes.txt",
                "text/plain",
                12,
                "a".repeat(64),
                "now",
                "now",
            )

        assertEquals(
            "path=data/notes.txt\nmimeType=text/plain\nsizeBytes=12\nsha256=${"a".repeat(64)}",
            file.toClipboardMetadata(),
        )
    }

    @Test
    fun `browser exposes direct files and child folders`() {
        val root = browseWorkspaceFiles(sampleFiles(), "")
        assertEquals(listOf("data"), root.directories.map { it.name })
        assertEquals(emptyList(), root.files)
        val data = browseWorkspaceFiles(sampleFiles(), "data")
        assertEquals(listOf("diagram.canvas", "notes.txt"), data.files.map { it.originalName })
        val empty = browseWorkspaceFiles(emptyList(), "", listOf("projects", "projects/demo"))
        assertEquals(listOf("projects"), empty.directories.map { it.name })
    }

    @Test
    fun `download row exposes pause and cancel controls`() {
        var paused = false
        var cancelled = false
        composeTestRule.setContent {
            MaterialTheme {
                WorkspaceDownloadRow(
                    download = WorkspaceDownload("download", "downloads/report.bin", WorkspaceDownloadState.DOWNLOADING, 50, 100),
                    onPause = { paused = true },
                    onResume = {},
                    onCancel = { cancelled = true },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Pause download").performClick()
        composeTestRule.onNodeWithContentDescription("Cancel download").performClick()
        assertEquals(true, paused)
        assertEquals(true, cancelled)
    }

    @Test
    fun `paused download resumes when its action is clicked`() {
        var resumed = false
        composeTestRule.setContent {
            MaterialTheme {
                WorkspaceDownloadRow(
                    download = WorkspaceDownload("download", "downloads/report.bin", WorkspaceDownloadState.PAUSED, 50, 100),
                    onPause = {},
                    onResume = { resumed = true },
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Resume download").performClick()

        assertEquals(true, resumed)
    }

    @Test
    fun `canvas file actions open rename and confirm deletion`() {
        val file = sampleFiles().first { it.mimeType == CANVAS_MIME_TYPE }
        val workspace = mockk<WorkspaceFilePort>(relaxed = true)
        val canvas = mockk<CanvasPort>(relaxed = true)
        var modal: Any? = null
        var refreshed = false

        composeTestRule.setContent {
            MaterialTheme {
                WorkspaceFileRow(
                    file,
                    workspace,
                    canvas,
                    ComposeModalRequester { modal = it },
                    refresh = { refreshed = true },
                    setStatus = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Open canvas document").performClick()
        verify(exactly = 1) { canvas.openDocument(file.id, null) }

        composeTestRule.onNodeWithContentDescription("Rename workspace file").performClick()
        assertEquals("Rename file", (modal as ComposeContentModal).title)

        composeTestRule.onNodeWithContentDescription("Delete workspace file").performClick()
        val confirmation = modal as ComposeConfirmationModal
        assertEquals("Delete workspace file?", confirmation.title)
        confirmation.onConfirm()
        verify(exactly = 1) { workspace.deleteFile(file.id) }
        assertEquals(true, refreshed)
    }

    @Test
    fun `copy file metadata button reports the copied file`() {
        val file = sampleFiles().first { it.mimeType != CANVAS_MIME_TYPE }
        var status = ""
        composeTestRule.setContent {
            MaterialTheme {
                WorkspaceFileRow(
                    file,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    ComposeModalRequester { },
                    refresh = {},
                    setStatus = { status = it },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Copy file metadata").performClick()

        assertEquals("Copied metadata for ${file.relativePath}", status)
    }

    @Test
    fun `workspace import button invokes platform picker callback`() {
        var pickerOpened = false
        composeTestRule.setContent {
            MaterialTheme { WorkspaceFileImportButton { pickerOpened = true } }
        }

        composeTestRule.onNodeWithContentDescription("Import file into current folder").performClick()

        assertEquals(true, pickerOpened)
    }

    @Test
    fun `panel renders protocol workspace values`() {
        val workspace = mockk<WorkspaceFilePort>()
        val filesLoaded = CompletionIdlingResource("workspace files")
        every { workspace.listFiles() } answers {
            filesLoaded.complete()
            sampleFiles()
        }
        every { workspace.listDirectories() } returns listOf("empty-folder")
        every { workspace.workspaceRoot() } returns "/tmp/workspace"
        every { workspace.activeDownloads() } returns emptyList()
        every { workspace.addDownloadListener(any()) } returns AutoCloseable { }
        every { workspace.addListener(any()) } returns AutoCloseable { }
        val canvas = mockk<CanvasPort>(relaxed = true)
        val activity = mockk<ActivityPort>(relaxed = true)
        composeTestRule.awaitCompletion(filesLoaded) {
            composeTestRule.setContent {
                MaterialTheme {
                    FilesPanel(workspace, canvas, ComposeModalRequester { }, activity)
                }
            }
        }
        composeTestRule.onNodeWithText("Folder / · 2 total · 0 visible").assertExists()
        composeTestRule.onNodeWithText("data").assertExists()
        composeTestRule.onNodeWithText("empty-folder").assertExists()
        composeTestRule.onNodeWithContentDescription("Create folder in current folder").assertExists()
    }

    @Test
    fun `folder navigation create and sync actions reach workspace service`() {
        val workspace = mockk<WorkspaceFilePort>()
        val filesLoaded = CompletionIdlingResource("workspace files")
        val syncCompleted = CompletionIdlingResource("workspace sync")
        every { workspace.listFiles() } answers {
            filesLoaded.complete()
            sampleFiles()
        }
        every { workspace.listDirectories() } returns listOf("data", "empty-folder")
        every { workspace.workspaceRoot() } returns "/tmp/workspace"
        every { workspace.activeDownloads() } returns emptyList()
        every { workspace.addDownloadListener(any()) } returns AutoCloseable { }
        every { workspace.addListener(any()) } returns AutoCloseable { }
        every { workspace.syncMetadataWithFilesystem() } answers {
            syncCompleted.complete()
            WorkspaceSyncResult(1, 2, 3, 4)
        }
        val canvas = mockk<CanvasPort>(relaxed = true)
        val activity = mockk<ActivityPort>(relaxed = true)
        var requestedModal: Any? = null

        composeTestRule.awaitCompletion(filesLoaded) {
            composeTestRule.setContent {
                MaterialTheme {
                    FilesPanel(workspace, canvas, ComposeModalRequester { requestedModal = it }, activity)
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Open folder data").performClick()
        composeTestRule.onNodeWithText("Folder data · 2 total · 2 visible").assertExists()
        composeTestRule.onNodeWithContentDescription("Create folder in current folder").performClick()
        assertEquals("Create folder", (requestedModal as ComposeContentModal).title)
        composeTestRule.onNodeWithContentDescription("Open parent folder").performClick()
        composeTestRule.onNodeWithText("Folder / · 2 total · 0 visible").assertExists()

        composeTestRule.awaitCompletion(syncCompleted) {
            composeTestRule.onNodeWithContentDescription("Sync workspace files").performClick()
        }
        verify(exactly = 1) { workspace.syncMetadataWithFilesystem() }
    }

    private fun sampleFiles() =
        listOf(
            WorkspaceFile("f1", "data/notes.txt", "notes.txt", "text/plain", 12, "abc123", "now", "now"),
            WorkspaceFile("f2", "data/diagram.canvas", "diagram.canvas", CANVAS_MIME_TYPE, 256, "def456", "now", "now"),
        )
}
