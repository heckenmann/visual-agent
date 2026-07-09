package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobQueueSnapshot
import de.heckenmann.visualagent.knowledge.Memory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
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
        every { manager.getSubAgent("created") } returns
            SubAgent(id = "created", name = "Coder", role = "Implementation", config = AgentConfig())
        every { manager.getSubAgent("missing") } returns null
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
    fun `agent update merges full config fields`() {
        every { manager.getSubAgent("agent-1") } returns
            SubAgent(id = "agent-1", name = "Coder", role = "Implementation", config = AgentConfig())
        every { manager.updateAgent("agent-1", null, null, any()) } returns true

        val result =
            AgentUpdateTool(manager).execute(
                """
                {
                    "id":"agent-1",
                    "timeout":120,
                    "maxRetries":5,
                    "memoryLimitMb":1024,
                    "provider":"ollama",
                    "model":"llama3",
                    "temperature":0.7,
                    "topP":0.9,
                    "maxTokens":4096,
                    "variant":"chat",
                    "options":{"seed":"42"},
                    "tools":["file:read","terminal"]
                }
                """.trimIndent(),
            )

        assertTrue(result.success)
        verify {
            manager.updateAgent(
                "agent-1",
                null,
                null,
                match { config: AgentConfig ->
                    config.timeout == 120 &&
                        config.maxRetries == 5 &&
                        config.memoryLimitMb == 1024L &&
                        config.provider == "ollama" &&
                        config.model == "llama3" &&
                        config.temperature == 0.7 &&
                        config.topP == 0.9 &&
                        config.maxTokens == 4096 &&
                        config.variant == "chat" &&
                        config.options == mapOf("seed" to "42") &&
                        config.tools == listOf("file:read", "terminal")
                },
            )
        }
    }

    @Test
    fun `agent log returns persisted log entries`() {
        val agent = SubAgent(id = "agent-1", name = "Coder", role = "Implementation")
        every { manager.getSubAgent("agent-1") } returns agent
        every { manager.memoryStore } returns
            mockk {
                every { searchMemories("agent:agent-1:log", 50) } returns
                    listOf(
                        Memory(
                            id = "m1",
                            content = "Worked on todo-1",
                            createdAt = Instant.now(),
                            tags = listOf("agent:agent-1:log"),
                        ),
                    )
            }

        val result = AgentLogTool(manager).execute("""{"id":"agent-1"}""")

        assertTrue(result.success)
        assertTrue(result.content.contains("Worked on todo-1"))
    }

    @Test
    fun `agent log fails for unknown agent`() {
        every { manager.getSubAgent("missing") } returns null

        val result = AgentLogTool(manager).execute("""{"id":"missing"}""")

        assertFalse(result.success)
    }
}
