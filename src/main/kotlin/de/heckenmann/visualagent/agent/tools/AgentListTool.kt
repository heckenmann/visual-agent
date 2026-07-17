package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Lists sub-agents and their current workload.
 *
 * The output now includes each agent's enabled tools, model, and template so the
 * main agent can choose the right agent for a todo.
 *
 * Use cases: UC-0000015, UC-0000018, UC-0000084.
 */
@Component
class AgentListTool(
    @param:Lazy private val agentManager: AgentManager,
    @param:Lazy private val agentToolConfigService: AgentToolConfigService,
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
        val queue = agentManager.getSubAgentJobQueueSnapshot()
        val agents =
            agentManager
                .getSubAgents()
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

    private fun formatAgentLine(agent: SubAgent): String {
        val tools = agentToolConfigService.toolsFor(agent).sortedBy { it.value }
        val model = agent.config.model?.ifBlank { null } ?: "default"
        val template = resolveTemplateName(agent)
        return buildString {
            append("- ${agent.id} | ${agent.name} | ${agent.role} | status=${agent.status} | model=$model | template=$template")
            agent.currentTodoId?.let { append(" | todo=$it") }
            agent.currentTask?.let { append(" | task=${it.take(120)}") }
            append(" | tools=[${tools.joinToString(",") { it.value }}]")
        }
    }

    private fun resolveTemplateName(agent: SubAgent): String {
        val configId = agentToolConfigService.findConfigIdFor(agent)
        return configId ?: agent.config.model?.ifBlank { null } ?: "default"
    }
}
