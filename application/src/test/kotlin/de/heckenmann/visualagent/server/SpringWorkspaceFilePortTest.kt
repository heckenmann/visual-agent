package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.workspace.WorkspaceDownloadService
import de.heckenmann.visualagent.workspace.WorkspaceFileActivity
import de.heckenmann.visualagent.workspace.WorkspaceFileActivityEventBus
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies that server-owned workspace mutations reach the presentation protocol. */
class SpringWorkspaceFilePortTest {
    @Test
    fun `server import stores only the selected user submitted payload`() {
        val fileService = mockk<WorkspaceFileService>()
        val bytes = "selected content".toByteArray()
        val record =
            WorkspaceFileRecord(
                id = "file-1",
                relativePath = "imports/notes.txt",
                originalName = "notes.txt",
                mimeType = "text/plain",
                sizeBytes = bytes.size.toLong(),
                sha256 = "hash",
                extractedText = null,
                importedAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        every { fileService.importFile("imports", "notes.txt", bytes) } returns record
        val port = SpringWorkspaceFilePort(fileService, mockk(relaxed = true), WorkspaceFileActivityEventBus())
        val imported = port.importFile("imports", "notes.txt", bytes)

        assertEquals("imports/notes.txt", imported.relativePath)
        verify(exactly = 1) { fileService.importFile("imports", "notes.txt", bytes) }
    }

    @Test
    fun `workspace activity listeners receive server mutations`() {
        val activityEvents = WorkspaceFileActivityEventBus()
        val port =
            SpringWorkspaceFilePort(
                mockk<WorkspaceFileService>(relaxed = true),
                mockk<WorkspaceDownloadService>(relaxed = true),
                activityEvents,
            )
        var notifications = 0
        val registration = port.addListener { notifications++ }

        activityEvents.publish(WorkspaceFileActivity("Workspace file written by JavaScript: report.md."))
        registration.close()
        activityEvents.publish(WorkspaceFileActivity("Workspace file written by JavaScript: ignored.md."))

        assertEquals(1, notifications)
    }
}
