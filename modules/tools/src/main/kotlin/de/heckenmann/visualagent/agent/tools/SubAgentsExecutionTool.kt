package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.AgentToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolAgentExecutionStatus
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult

/**
 * Pauses, resumes, or inspects sub-agent execution gates.
 *
 * The main agent can control the global gate with `{\"action\":\"pause\"}` or
 * target one worker with `{\"action\":\"pause\",\"agentId\":\"...\"}`.
 */
@AgentTool
class SubAgentsExecutionTool(
    private val agents: AgentToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("subagents:execution"),
            name = ToolId("subagents:execution").toFunctionName(),
            description =
                "Inspect or change sub-agent execution. Input: " +
                    "{\"action\":\"status|pause|resume\",\"agentId\":\"optional-agent-id\"}. " +
                    "Without agentId, pause/resume controls all sub-agents. " +
                    "The main agent remains active and individual pauses survive global resume.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val action = input.string("action")?.ifBlank { null } ?: "status"
        val agentId = input.string("agentId")
        return runCatching { agents.control(action, agentId) }
            .fold(
                onSuccess = { success("subagents:execution", formatStatus(it)) },
                onFailure = { failure("subagents:execution", it.message ?: "Execution control failed") },
            )
    }

    private fun formatStatus(status: ToolAgentExecutionStatus): String =
        buildString {
            appendLine("Global state: ${status.globalState}")
            status.agentId?.let { appendLine("Agent $it state: ${status.agentState}") }
            appendLine("Effective state: ${status.effectiveState}")
            appendLine("Pause reason: ${status.pauseReason}")
            append("Individually paused agents: ${status.pausedAgentIds.ifEmpty { listOf("none") }.joinToString(", ")}")
        }
}
