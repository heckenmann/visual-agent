package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Lists sub-agents and their current workload.
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

/**
 * Dynamically creates and runs a sub-agent under the configured parallelism limit.
 */
@Component
class AgentStartTool(
    @param:Lazy private val agentManager: AgentManager,
) : VisualAgentTool {
    override val managesExecution: Boolean = true
    override val definition =
        ToolDefinition(
            id = ToolId("agent:start"),
            name = ToolId("agent:start").toFunctionName(),
            description =
                "Create and run a sub-agent. Input: " +
                    "{\"name\":\"Coder\",\"role\":\"Implementation\",\"templateName\":\"coder\",\"content\":\"Task\",\"async\":false}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val name = input.requiredString("name")
        val role = input.requiredString("role")
        val templateName = input.string("templateName")?.takeIf(String::isNotBlank) ?: "researcher"
        val content = input.requiredString("content")
        if (input.boolean("async") == true) {
            val jobId = agentManager.enqueueAgentJob(name, role, templateName, content)
            return success("agent:start", "Queued sub-agent job $jobId")
        }
        val result = runBlocking { agentManager.startAgentJob(name, role, templateName, content) }
        return success("agent:start", "Sub-agent ${result.agentName} (${result.agentId}) completed.\n${result.content}")
    }
}

/**
 * Sends a direct instruction to an existing sub-agent.
 */
@Component
class AgentMessageTool(
    @param:Lazy private val agentManager: AgentManager,
) : VisualAgentTool {
    override val managesExecution: Boolean = true
    override val definition =
        ToolDefinition(
            id = ToolId("agent:message"),
            name = ToolId("agent:message").toFunctionName(),
            description = "Run a job on an existing sub-agent. Input: {\"agentId\":\"123\",\"content\":\"Do X\",\"async\":false}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val agentId = input.requiredString("agentId")
        val content = input.requiredString("content")
        if (input.boolean("async") == true) {
            val jobId = agentManager.enqueueAgentJob(agentId, content)
            return success("agent:message", "Queued sub-agent job $jobId for agent $agentId")
        }
        return success("agent:message", runBlocking { agentManager.runAgentJob(agentId, content) }.content)
    }
}
