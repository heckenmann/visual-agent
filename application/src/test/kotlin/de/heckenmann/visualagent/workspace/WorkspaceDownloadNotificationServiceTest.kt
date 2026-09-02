package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.protocol.DownloadActivity
import de.heckenmann.visualagent.protocol.DownloadActivityStatus
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/** Verifies that download lifecycle transitions reach the main-agent conversation. */
class WorkspaceDownloadNotificationServiceTest {
    @Test
    fun `completed download is appended as a main-agent notification`() {
        val eventBus = WorkspaceDownloadEventBus()
        val manager = mockk<AgentManager>(relaxed = true)
        val notifications = WorkspaceDownloadNotificationService(eventBus, manager)

        eventBus.publish(
            DownloadActivity(
                id = "download-1",
                relativePath = "downloads/report.pdf",
                status = DownloadActivityStatus.COMPLETED,
                downloadedBytes = 42,
                totalBytes = 42,
                mimeType = "application/pdf",
                sizeBytes = 42,
                sha256 = "abc123",
                validationResult = "accepted",
            ),
        )

        verify {
            manager.appendSystemMessage(
                match {
                    it.contains("Workspace download completed: downloads/report.pdf") &&
                        it.contains("available to the main agent")
                },
                match {
                    it.orEmpty().contains("workspace_download") &&
                        it.contains("downloadedBytes") &&
                        it.contains("mimeType") &&
                        it.contains("validationResult")
                },
                ConversationContextPolicy.SUMMARY_SOURCE,
            )
        }
        notifications.destroy()
    }

    @Test
    fun `download progress is retained for audit but excluded from provider context`() {
        val eventBus = WorkspaceDownloadEventBus()
        val manager = mockk<AgentManager>(relaxed = true)
        val notifications = WorkspaceDownloadNotificationService(eventBus, manager)

        eventBus.publish(
            DownloadActivity(
                id = "download-1",
                relativePath = "downloads/report.pdf",
                status = DownloadActivityStatus.STARTED,
                downloadedBytes = 0,
            ),
        )

        verify {
            manager.appendSystemMessage(
                match { it.contains("Workspace download started") },
                any(),
                ConversationContextPolicy.AUDIT_ONLY,
            )
        }
        notifications.destroy()
    }
}
