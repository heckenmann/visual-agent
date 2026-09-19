package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnboardingAgentServiceTest {
    private val agentManager = mockk<AgentManager>()
    private val service = OnboardingAgentService(agentManager)

    @Test
    fun `agents expose safe persisted summaries`() {
        every { agentManager.getSubAgents() } returns listOf(SubAgent("agent-1", "Researcher", "Research topics"))

        val result = service.agents().single()

        assertEquals("agent-1", result.id)
        assertEquals("Researcher", result.name)
        assertEquals("Research topics", result.role)
    }

    @Test
    fun `creation request uses normal main agent conversation flow and refreshes inventory`() =
        runTest {
            every { agentManager.getSubAgents() } returns listOf(SubAgent("agent-1", "Researcher", "Research topics"))
            coEvery { agentManager.sendMessage(any(), any()) } returns "Created the Researcher."

            val result = service.createAgent("  Create a Researcher.  ")

            coVerify { agentManager.sendMessage("Create a Researcher.", null) }
            assertEquals("Created the Researcher.", result.message)
            assertEquals(listOf("Researcher"), result.agents.map { it.name })
        }

    @Test
    fun `blank creation request is rejected before model invocation`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { service.createAgent("  ") }
            coVerify(exactly = 0) { agentManager.sendMessage(any(), any()) }
        }
}
