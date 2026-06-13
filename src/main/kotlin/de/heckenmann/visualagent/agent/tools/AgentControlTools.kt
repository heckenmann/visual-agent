package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Tool set for main-agent control over sub-agents.
 *
 * These tools intentionally avoid direct workspace, file, terminal, web, or todo mutation
 * capabilities so that the main agent can only steer work through sub-agent orchestration.
 */
@Component
class AgentListTool(
    @Lazy
    private val agentManager: AgentManager,
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
                        append("- ")
                        append(agent.id)
                        append(" | ")
                        append(agent.name)
                        append(" | ")
                        append(agent.role)
                        append(" | status=")
                        append(agent.status)
                        agent.currentTodoId?.let { append(" | todo=").append(it) }
                        agent.currentTask?.let { append(" | task=").append(it.take(120)) }
                    }
                }
        val content =
            buildString {
                appendLine("Jobs: active=${queue.active}, queued=${queue.queued}")
                append(agents.ifBlank { "No sub-agents found." })
            }
        return success("agent:list", content)
    }
}

/**
 * Dynamically creates and runs a sub-agent under the configured parallelism limit.
 */
@Component
class AgentStartTool(
    @Lazy
    private val agentManager: AgentManager,
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
        val templateName = input.string("templateName")?.takeIf { it.isNotBlank() } ?: "researcher"
        val content = input.requiredString("content")
        return if (input.boolean("async") == true) {
            val jobId = agentManager.enqueueAgentJob(name, role, templateName, content)
            success("agent:start", "Queued sub-agent job $jobId")
        } else {
            val result =
                runBlocking {
                    agentManager.startAgentJob(name, role, templateName, content)
                }
            success(
                "agent:start",
                "Sub-agent ${result.agentName} (${result.agentId}) completed.\n${result.content}",
            )
        }
    }
}

/**
 * Creates a new sub-agent from a template.
 */
@Component
class AgentCreateTool(
    @Lazy
    private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:create"),
            name = ToolId("agent:create").toFunctionName(),
            description = "Create a sub-agent. Input: {\"name\":\"Coder\",\"role\":\"Implementation\",\"templateName\":\"coder\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val name = input.requiredString("name")
        val role = input.requiredString("role")
        val templateName = input.string("templateName")?.takeIf { it.isNotBlank() } ?: "researcher"
        val created = agentManager.createAgent(name, role, templateName)
        return success("agent:create", "Created agent ${created.id} ($name, template=$templateName)")
    }
}

/**
 * Updates an existing sub-agent.
 */
@Component
class AgentUpdateTool(
    @Lazy
    private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:update"),
            name = ToolId("agent:update").toFunctionName(),
            description = "Update a sub-agent. Input: {\"id\":\"123\",\"name\":\"Coder\",\"role\":\"Implementation\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val id = input.requiredString("id")
        val name = input.string("name")
        val role = input.string("role")
        val templateName = input.string("templateName")?.takeIf { it.isNotBlank() }
        val config =
            templateName?.let {
                runCatching { AgentConfig.fromTemplate(it) }.getOrDefault(AgentConfig())
            }
        val updated = agentManager.updateAgent(id, name = name, role = role, config = config)
        return if (updated) {
            success("agent:update", "Updated agent $id")
        } else {
            failure("agent:update", "Agent not found")
        }
    }
}

/**
 * Deletes a sub-agent.
 */
@Component
class AgentDeleteTool(
    @Lazy
    private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:delete"),
            name = ToolId("agent:delete").toFunctionName(),
            description = "Delete a sub-agent. Input: {\"id\":\"123\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val id = parseObject(inputJson).requiredString("id")
        return if (agentManager.deleteAgent(id)) {
            success("agent:delete", "Deleted agent $id")
        } else {
            failure("agent:delete", "Agent not found")
        }
    }
}

/**
 * Sends a direct instruction to a sub-agent.
 */
@Component
class AgentMessageTool(
    @Lazy
    private val agentManager: AgentManager,
) : VisualAgentTool {
    override val managesExecution: Boolean = true

    override val definition =
        ToolDefinition(
            id = ToolId("agent:message"),
            name = ToolId("agent:message").toFunctionName(),
            description =
                "Run a job on an existing sub-agent. Input: " +
                    "{\"agentId\":\"123\",\"content\":\"Do X\",\"async\":false}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val agentId = input.requiredString("agentId")
        val content = input.requiredString("content")
        return if (input.boolean("async") == true) {
            val jobId = agentManager.enqueueAgentJob(agentId, content)
            success("agent:message", "Queued sub-agent job $jobId for agent $agentId")
        } else {
            val result =
                runBlocking {
                    agentManager.runAgentJob(agentId, content)
                }
            success("agent:message", result.content)
        }
    }
}

/**
 * Assigns a todo to a specific sub-agent.
 */
@Component
class AgentAssignTodoTool(
    @Lazy
    private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:assign-todo"),
            name = ToolId("agent:assign-todo").toFunctionName(),
            description = "Assign a todo to a specific sub-agent. Input: {\"todoId\":\"todo-1\",\"agentId\":\"agent-1\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val todoId = input.requiredString("todoId")
        val agentId = input.requiredString("agentId")
        return if (agentManager.assignTodoToAgent(todoId, agentId)) {
            success("agent:assign-todo", "Assigned todo $todoId to agent $agentId")
        } else {
            failure("agent:assign-todo", "Assignment failed")
        }
    }
}

/**
 * Assigns the next pending todo to an idle sub-agent.
 */
@Component
class AgentAssignNextTodoTool(
    @Lazy
    private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:assign-next-todo"),
            name = ToolId("agent:assign-next-todo").toFunctionName(),
            description = "Assign the next pending todo to an idle sub-agent.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        if (agentManager.assignNextTodo()) {
            success("agent:assign-next-todo", "Assigned next pending todo")
        } else {
            failure("agent:assign-next-todo", "No todo could be assigned")
        }
}

/**
 * Assigns all pending todos up to the available worker capacity.
 */
@Component
class AgentAssignAllTodosTool(
    @Lazy
    private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:assign-all-todos"),
            name = ToolId("agent:assign-all-todos").toFunctionName(),
            description = "Assign all pending todos up to the available worker capacity.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val count = agentManager.assignAllPendingTodos()
        return success("agent:assign-all-todos", "Assigned $count todo(s)")
    }
}
