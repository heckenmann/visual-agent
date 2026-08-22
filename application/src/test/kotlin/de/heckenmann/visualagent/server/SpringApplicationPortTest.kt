package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/** Verifies delegation from the application composition port to the agent manager. */
class SpringApplicationPortTest {
    @Test
    fun `cancel active work delegates to agent manager`() {
        val manager = mockk<AgentManager>(relaxed = true)
        val lifecycle = mockk<de.heckenmann.visualagent.protocol.LifecyclePort>()
        every { lifecycle.closing } returns false
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
                lifecycle = lifecycle,
                agentManager = manager,
            )

        port.cancelActiveWork()

        verify(exactly = 1) { manager.cancelAllRunningActions() }
        verify(exactly = 1) { manager.cancelAllActiveTodos() }
    }

    @Test
    fun `cancel active work cancels the manager scope during shutdown`() {
        val manager = mockk<AgentManager>(relaxed = true)
        val lifecycle = mockk<de.heckenmann.visualagent.protocol.LifecyclePort>()
        every { lifecycle.closing } returns true
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
                lifecycle = lifecycle,
                agentManager = manager,
            )

        port.cancelActiveWork()

        verify(exactly = 1) { manager.cancelActiveWork() }
        verify(exactly = 0) { manager.cancelAllRunningActions() }
        verify(exactly = 0) { manager.cancelAllActiveTodos() }
    }
}
