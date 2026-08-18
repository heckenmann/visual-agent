package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.AgentToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolAgent
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult

/**
 * Lists sub-agents and their current workload.
 *
 * The output now includes each agent's enabled tools, model, and template so the
 * main agent can choose the right agent for a todo.
 *
 * Use cases: UC-0000015, UC-0000018, UC-0000084.
 */
@AgentTool
class AgentListTool(
    private val agents: AgentToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:list"),
            name = ToolId("agent:list").toFunctionName(),
            description =
                "List all sub-agents with their status, assigned tools, model, and current task. " +
                    "No input parameters required. " +
                    "Input: {}. " +
                    "Use this first to discover available agents before assigning work.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val queue = agents.queue()
        val agents =
            agents
                .list()
                .sortedBy { it.id }
                .joinToString("\n") { agent -> formatAgentLine(agent) }
        return success(
            "agent:list",
            buildString {
                appendLine("Jobs: active=${queue.active}, queued=${queue.queued}")
                append(agents.ifBlank { "No sub-agents found." })
            },
        )
    }

    private fun formatAgentLine(agent: ToolAgent): String {
        val tools = agents.tools(agent.id).sorted()
        val model = agent.config.model?.ifBlank { null } ?: "inherited"
        val template = resolveTemplateName(agent)
        return buildString {
            append("- ${agent.id} | ${agent.name} | ${agent.role} | status=${agent.status} | model=$model | template=$template")
            agent.currentTodoId?.let { append(" | todo=$it") }
            agent.currentTask?.let { append(" | task=${it.take(120)}") }
            append(" | tools=[${tools.joinToString(",")}]")
        }
    }

    private fun resolveTemplateName(agent: ToolAgent): String {
        val configId = agents.configId(agent.id)
        return configId ?: agent.config.model?.ifBlank { null } ?: "inherited"
    }
}
