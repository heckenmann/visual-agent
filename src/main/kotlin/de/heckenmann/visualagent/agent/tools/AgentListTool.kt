package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Lists sub-agents and their current workload.
 *
 * Use cases: UC-0000015, UC-0000018.
 */
@Component
class AgentListTool(
    @param:Lazy private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:list"),
            name = ToolId("agent:list").toFunctionName(),
            description = "List all sub-agents with their current status, task, and todo assignment.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val queue = agentManager.getSubAgentJobQueueSnapshot()
        val agents =
            agentManager
                .getSubAgents()
                .sortedBy { it.id }
                .joinToString("\n") { agent ->
                    buildString {
                        append("- ${agent.id} | ${agent.name} | ${agent.role} | status=${agent.status}")
                        agent.currentTodoId?.let { append(" | todo=$it") }
                        agent.currentTask?.let { append(" | task=${it.take(120)}") }
                    }
                }
        return success(
            "agent:list",
            "Jobs: active=${queue.active}, queued=${queue.queued}\n${agents.ifBlank { "No sub-agents found." }}",
        )
    }
}
