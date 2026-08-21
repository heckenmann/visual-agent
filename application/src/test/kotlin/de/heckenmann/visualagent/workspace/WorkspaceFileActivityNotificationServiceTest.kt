package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.AgentManager
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/** Verifies that workspace mutations become passive main-agent conversation context. */
class WorkspaceFileActivityNotificationServiceTest {
    @Test
    fun `workspace mutation is appended without starting an agent request`() {
        val eventBus = WorkspaceFileActivityEventBus()
        val agentManager = mockk<AgentManager>(relaxed = true)
        val notifications = WorkspaceFileActivityNotificationService(eventBus, agentManager)

        eventBus.publish(WorkspaceFileActivity("Workspace folder created: projects/demo."))

        verify(exactly = 1) { agentManager.appendSystemMessage("Workspace folder created: projects/demo.") }
        notifications.destroy()
    }
}
