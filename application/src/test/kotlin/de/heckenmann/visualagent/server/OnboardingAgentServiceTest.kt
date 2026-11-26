package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationAvailability
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnboardingAgentServiceTest {
    private val agentManager = mockk<AgentManager>()
    private val providerCatalog = mockk<ProviderCatalogService>()
    private val agentToolConfig = mockk<AgentToolConfigService>()
    private val toolEvents = ToolEventBus()
    private val service = OnboardingAgentService(agentManager, providerCatalog, agentToolConfig, toolEvents)

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
            every { agentManager.getSubAgents() } returns
                listOf(
                    SubAgent("agent-1", "Researcher", "Research topics"),
                    SubAgent("agent-2", "Writer", "Write clearly"),
                )
            coEvery { agentManager.sendMessage(any(), requestId = any()) } coAnswers {
                publishCreation(secondArg(), success = true)
                "Created the Writer."
            }

            val result = service.createAgent("  Create a Writer.  ")

            coVerify { agentManager.sendMessage("Create a Writer.", requestId = any()) }
            assertEquals("Created the Writer.", result.message)
            assertEquals(listOf("Researcher", "Writer"), result.agents.map { it.name })
            assertEquals(true, result.agentCreated)
        }

    @Test
    fun `tool-capable model can use direct agent tools when tool help is disabled`() {
        every { providerCatalog.activeModelSupportsToolCalling() } returns true
        every { agentToolConfig.mainAgentTools() } returns setOf(ToolId("agent:list"), ToolId("agent:create"))

        assertEquals(OnboardingAgentCreationAvailability(available = true), service.agentCreationAvailability())
    }

    @Test
    fun `model without tool calling can select another model`() {
        every { providerCatalog.activeModelSupportsToolCalling() } returns false

        val availability = service.agentCreationAvailability()

        assertEquals(false, availability.available)
        assertEquals(true, availability.canSelectAnotherModel)
        assertEquals(true, availability.reason?.contains("cannot call tools"))
    }

    @Test
    fun `globally disabled required tools prevent creation`() {
        every { providerCatalog.activeModelSupportsToolCalling() } returns true
        every { agentToolConfig.mainAgentTools() } returns setOf("agent:list", "tool:help").map(::ToolId).toSet()

        val availability = service.agentCreationAvailability()

        assertEquals(false, availability.available)
        assertEquals(false, availability.canSelectAnotherModel)
        assertEquals(true, availability.reason?.contains("tool settings"))
    }

    @Test
    fun `another request's creation and this request's failed creation do not count`() =
        runTest {
            val unrelatedAgent = SubAgent("agent-1", "Writer", "Write clearly")
            every { agentManager.getSubAgents() } returns emptyList()
            coEvery { agentManager.sendMessage(any(), requestId = any()) } coAnswers {
                every { agentManager.getSubAgents() } returns listOf(unrelatedAgent)
                publishCreation("unrelated-request", success = true)
                publishCreation(secondArg(), success = false)
                "Here are some suggestions."
            }

            val result = service.createAgent("Create a Researcher")

            assertEquals(false, result.agentCreated)
            assertEquals(listOf("agent-1"), result.agents.map { it.id })
        }

    @Test
    fun `blank creation request is rejected before model invocation`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { service.createAgent("  ") }
            coVerify(exactly = 0) { agentManager.sendMessage(any(), requestId = any()) }
        }

    private fun publishCreation(
        requestId: String,
        success: Boolean,
    ) {
        toolEvents.publish(
            ToolCallEvent(
                toolId = "agent:create",
                functionName = "agent_create",
                requestId = requestId,
                inputJson = "{}",
                context = mapOf("requestId" to requestId),
                result = ToolResult("agent:create", success, ""),
                startedAtUtc = Instant.EPOCH,
                finishedAtUtc = Instant.EPOCH,
                durationMillis = 0,
            ),
        )
    }
}
