package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/** Verifies delegation from the application composition port to the agent manager. */
class SpringApplicationPortTest {
    @Test
    fun `cancel active work delegates to agent manager`() {
        val manager = mockk<AgentManager>(relaxed = true)
        val port =
            SpringApplicationPort(
                conversation = mockk(),
                todos = mockk(),
                agents = mockk(),
                providers = mockk(),
                settings = mockk(),
                workspaceFiles = mockk(),
                canvas = mockk(),
                layout = mockk(),
                activity = mockk(),
                lifecycle = mockk(),
                agentManager = manager,
            )

        port.cancelActiveWork()

        verify(exactly = 1) { manager.cancelAllRunningActions() }
        verify(exactly = 1) { manager.cancelAllActiveTodos() }
    }
}
