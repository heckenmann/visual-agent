package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Creates a new sub-agent from a template.
 */
@Component
class AgentCreateTool(
    @param:Lazy private val agentManager: AgentManager,
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
        val templateName = input.string("templateName")?.takeIf(String::isNotBlank) ?: "researcher"
        val created = agentManager.createAgent(name, role, templateName)
        return success("agent:create", "Created agent ${created.id} ($name, template=$templateName)")
    }
}

/**
 * Updates an existing sub-agent.
 */
@Component
class AgentUpdateTool(
    @param:Lazy private val agentManager: AgentManager,
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
        val templateName = input.string("templateName")?.takeIf(String::isNotBlank)
        val config = templateName?.let(AgentConfig::fromTemplate)
        val updated = agentManager.updateAgent(id, input.string("name"), input.string("role"), config)
        return if (updated) success("agent:update", "Updated agent $id") else failure("agent:update", "Agent not found")
    }
}

/**
 * Deletes a sub-agent.
 */
@Component
class AgentDeleteTool(
    @param:Lazy private val agentManager: AgentManager,
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
