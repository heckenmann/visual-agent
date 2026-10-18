package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.success
import de.heckenmann.visualagent.knowledge.SubAgentConfigStore
import de.heckenmann.visualagent.task.TaskOperation
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.ObjectProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskOperationSupportTest {
    @Test
    fun `schema enumerations restrict known actions while missing declarations remain unknown`() {
        fun tool(
            id: String,
            schema: String,
        ) = object : VisualAgentTool {
            override val definition = ToolDefinition(ToolId(id), id, "Test", schema)

            override fun execute(
                inputJson: String,
                context: Map<String, Any>,
            ) = success(id, "OK")
        }
        val registry =
            ToolRegistry(
                listOf(
                    tool("known", """{"properties":{"action":{"enum":["read"]}}}"""),
                    tool("unknown", "{}"),
                ),
                ToolEventBus(),
            )
        val reference = mockk<ObjectProvider<ToolRegistry>>()
        every { reference.ifAvailable } returns registry
        val service = AgentToolConfigService(mockk<SubAgentConfigStore>(relaxed = true), registry = reference)
        val agent = SubAgent("worker", "Worker", "Role")
        assertTrue(service.supportsOperation(agent, TaskOperation("known", "read")))
        assertFalse(service.supportsOperation(agent, TaskOperation("known", "write")))
        assertTrue(service.supportsOperation(agent, TaskOperation("unknown", "read")))
        assertFalse(service.supportsOperation(agent, TaskOperation("missing", "read")))
    }
}
