package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies the stable mapping from internal tool IDs to provider function names. */
class ToolRegistryNamingTest {
    @Test
    fun `internal IDs normalize to portable lowercase snake case`() {
        assertEquals("agent_list", ToolId("agent:list").toFunctionName())
        assertEquals("workspace_file", ToolId("WORKSPACE:FILE").toFunctionName())
    }

    @Test
    fun `tool ID must produce a valid provider function name`() {
        assertFailsWith<IllegalArgumentException> { ToolId("1agent").toFunctionName() }
    }

    @Test
    fun `registry rejects duplicate internal tool IDs`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                registry(FakeTool("agent:list"), FakeTool("agent:list"))
            }

        assertContains(failure.message.orEmpty(), "duplicate internal tool IDs")
    }

    @Test
    fun `registry rejects IDs that normalize to one provider function name`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                registry(FakeTool("agent:list"), FakeTool("agent_list"))
            }

        assertContains(failure.message.orEmpty(), "provider function name collisions")
    }

    @Test
    fun `registry rejects provider names outside the canonical mapping`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                registry(FakeTool("agent:list", "agent-list"))
            }

        assertContains(failure.message.orEmpty(), "must use provider function name 'agent_list'")
    }

    private fun registry(vararg tools: VisualAgentTool): ToolRegistry = ToolRegistry(tools.toList(), ToolEventBus())

    private class FakeTool(
        id: String,
        name: String = ToolId(id).toFunctionName(),
    ) : VisualAgentTool {
        override val definition = ToolDefinition(ToolId(id), name, "Fake $id", "{}")

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = ToolResult(definition.id.value, true, "ok")
    }
}
