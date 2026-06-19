package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentJobResult
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobQueueSnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentControlToolsTest {
    private val manager = mockk<AgentManager>()

    @Test
    fun `agent list reports queue and workload`() {
        val agent =
            SubAgent(
                id = "agent-1",
                name = "Coder",
                role = "Implementation",
                status = AgentStatus.BUSY,
                currentTask = "Implement queue",
                currentTodoId = "todo-1",
            )
        every { manager.getSubAgentJobQueueSnapshot() } returns SubAgentJobQueueSnapshot(active = 1, queued = 2)
        every { manager.getSubAgents() } returns listOf(agent)

        val result = AgentListTool(manager).execute("{}")

        assertTrue(result.content.contains("active=1, queued=2"))
        assertTrue(result.content.contains("todo=todo-1"))
        assertTrue(result.content.contains("task=Implement queue"))
    }

    @Test
    fun `agent lifecycle tools expose success and missing-agent results`() {
        every { manager.createAgent("Coder", "Implementation", "researcher") } returns
            SubAgent(id = "created", name = "Coder", role = "Implementation")
        every { manager.updateAgent("created", "Senior Coder", null, any()) } returns true
        every { manager.updateAgent("missing", null, null, null) } returns false
        every { manager.deleteAgent("created") } returns true
        every { manager.deleteAgent("missing") } returns false

        assertTrue(AgentCreateTool(manager).execute("""{"name":"Coder","role":"Implementation"}""").success)
        assertTrue(
            AgentUpdateTool(manager)
                .execute("""{"id":"created","name":"Senior Coder","templateName":"coder"}""")
                .success,
        )
        assertFalse(AgentUpdateTool(manager).execute("""{"id":"missing"}""").success)
        assertTrue(AgentDeleteTool(manager).execute("""{"id":"created"}""").success)
        assertFalse(AgentDeleteTool(manager).execute("""{"id":"missing"}""").success)
    }

    @Test
    fun `execution tools support synchronous and queued jobs`() {
        coEvery { manager.startAgentJob("Coder", "Implementation", "coder", "Build") } returns
            AgentJobResult("agent-1", "Coder", "built")
        every { manager.enqueueAgentJob("Coder", "Implementation", "coder", "Build") } returns "job-1"
        coEvery { manager.runAgentJob("agent-1", "Review") } returns AgentJobResult("agent-1", "Coder", "reviewed")
        every { manager.enqueueAgentJob("agent-1", "Review") } returns "job-2"

        assertTrue(
            AgentStartTool(manager)
                .execute("""{"name":"Coder","role":"Implementation","templateName":"coder","content":"Build"}""")
                .content
                .contains("built"),
        )
        assertTrue(
            AgentStartTool(manager)
                .execute("""{"name":"Coder","role":"Implementation","templateName":"coder","content":"Build","async":true}""")
                .content
                .contains("job-1"),
        )
        assertTrue(
            AgentMessageTool(manager)
                .execute("""{"agentId":"agent-1","content":"Review"}""")
                .content
                .contains("reviewed"),
        )
        assertTrue(
            AgentMessageTool(manager)
                .execute("""{"agentId":"agent-1","content":"Review","async":true}""")
                .content
                .contains("job-2"),
        )
    }

    @Test
    fun `todo assignment tools preserve manager outcomes`() {
        every { manager.assignTodoToAgent("todo-1", "agent-1") } returns true
        every { manager.assignTodoToAgent("missing", "agent-1") } returns false
        every { manager.assignNextTodo() } returnsMany listOf(true, false)
        every { manager.assignAllPendingTodos() } returns 3

        assertTrue(
            AgentAssignTodoTool(manager)
                .execute("""{"todoId":"todo-1","agentId":"agent-1"}""")
                .success,
        )
        assertFalse(
            AgentAssignTodoTool(manager)
                .execute("""{"todoId":"missing","agentId":"agent-1"}""")
                .success,
        )
        assertTrue(AgentAssignNextTodoTool(manager).execute("{}").success)
        assertFalse(AgentAssignNextTodoTool(manager).execute("{}").success)
        assertTrue(AgentAssignAllTodosTool(manager).execute("{}").content.contains("3"))
        verify(exactly = 1) { manager.assignAllPendingTodos() }
    }
}
