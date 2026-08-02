package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobQueueSnapshot
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
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
    private val agentToolConfigService = mockk<de.heckenmann.visualagent.agent.config.AgentToolConfigService>()

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
        every { agentToolConfigService.toolsFor(agent) } returns setOf(ToolId("file:write"), ToolId("terminal"))
        every { agentToolConfigService.findConfigIdFor(agent) } returns "coder"

        val result = AgentListTool(manager, agentToolConfigService).execute("{}")

        assertTrue(result.content.contains("active=1, queued=2"))
        assertTrue(result.content.contains("todo=todo-1"))
        assertTrue(result.content.contains("task=Implement queue"))
    }

    @Test
    fun `agent lifecycle tools expose success and missing-agent results`() {
        val createdAgent =
            SubAgent(id = "created", name = "Coder", role = "Implementation", config = AgentConfig.fromTemplate("researcher"))
        val updatedAgent =
            SubAgent(id = "created", name = "Senior Coder", role = "Implementation", config = AgentConfig.fromTemplate("coder"))
        every { manager.createAgent("Coder", "Implementation", "researcher") } returns createdAgent
        every { manager.agentToolConfigService } returns agentToolConfigService
        every { agentToolConfigService.toolsFor(createdAgent) } returns setOf(ToolId("file:read"))
        every { agentToolConfigService.toolsFor(updatedAgent) } returns setOf(ToolId("file:write"))
        every { manager.updateAgent("created", "Senior Coder", null, any()) } returns true
        every { manager.updateAgent("missing", null, null, null) } returns false
        every { manager.getSubAgent("created") } returnsMany listOf(createdAgent, updatedAgent)
        every { manager.getSubAgent("missing") } returns null
        every { manager.deleteAgent("created") } returns true
        every { manager.deleteAgent("missing") } returns false

        val createResult = AgentCreateTool(manager).execute("""{"name":"Coder","role":"Implementation"}""")
        assertTrue(createResult.success)
        assertTrue(createResult.content.contains("To assign work, create a todo"))

        val updateResult = AgentUpdateTool(manager).execute("""{"id":"created","name":"Senior Coder","templateName":"coder"}""")
        assertTrue(updateResult.success)
        assertTrue(updateResult.content.contains("Assigned tools:"))
        assertFalse(AgentUpdateTool(manager).execute("""{"id":"missing"}""").success)
        assertTrue(AgentDeleteTool(manager).execute("""{"id":"created"}""").success)
        assertFalse(AgentDeleteTool(manager).execute("""{"id":"missing"}""").success)
    }

    @Test
    fun `agent update merges full config fields`() {
        val original = SubAgent(id = "agent-1", name = "Coder", role = "Implementation", config = AgentConfig())
        val updated =
            SubAgent(
                id = "agent-1",
                name = "Coder",
                role = "Implementation",
                config =
                    AgentConfig(
                        timeout = 120,
                        maxRetries = 5,
                        memoryLimitMb = 1024,
                        provider = "ollama",
                        model = "llama3",
                        temperature = 0.7,
                        topP = 0.9,
                        maxTokens = 4096,
                        variant = "chat",
                        options = mapOf("seed" to "42"),
                        tools = listOf("file:read", "terminal"),
                    ),
            )
        every { manager.getSubAgent("agent-1") } returns original
        every { manager.agentToolConfigService } returns agentToolConfigService
        every { agentToolConfigService.toolsFor(updated) } returns setOf(ToolId("file:read"), ToolId("terminal"))
        every { manager.updateAgent("agent-1", null, null, any()) } returns true
        every { manager.getSubAgent("agent-1") } returns updated

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
        assertTrue(result.content.contains("Assigned tools:"))
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

    @Test
    fun `agent show returns full details for an agent`() {
        val agent =
            SubAgent(
                id = "agent-1",
                name = "CanvasCleaner",
                role = "Canvas operations",
                status = AgentStatus.IDLE,
                config = AgentConfig.fromTemplate("coder"),
            )
        every { manager.getSubAgent("agent-1") } returns agent
        every { manager.memoryStore } returns
            mockk {
                every { searchMemories("agent:agent-1:log", 10) } returns emptyList()
            }
        every { manager.agentToolConfigService } returns agentToolConfigService
        every { agentToolConfigService.toolsFor(agent) } returns setOf(ToolId("canvas"), ToolId("file:read"))
        every { agentToolConfigService.findConfigIdFor(agent) } returns "coder"
        every { agentToolConfigService.descriptionForConfigId("coder") } returns "Implement code changes"

        val result = AgentShowTool(manager, agentToolConfigService).execute("""{"id":"agent-1"}""")

        assertTrue(result.success)
        assertTrue(result.content.contains("CanvasCleaner"))
        assertTrue(result.content.contains("canvas"))
        assertTrue(result.content.contains("coder"))
    }

    @Test
    fun `agent show fails for unknown agent`() {
        every { manager.getSubAgent("missing") } returns null

        val result = AgentShowTool(manager, agentToolConfigService).execute("""{"id":"missing"}""")

        assertFalse(result.success)
    }
}
