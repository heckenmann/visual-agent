package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.workspace.WorkspaceDownloadService
import de.heckenmann.visualagent.workspace.WorkspaceFileActivity
import de.heckenmann.visualagent.workspace.WorkspaceFileActivityEventBus
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies that server-owned workspace mutations reach the presentation protocol. */
class SpringWorkspaceFilePortTest {
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
