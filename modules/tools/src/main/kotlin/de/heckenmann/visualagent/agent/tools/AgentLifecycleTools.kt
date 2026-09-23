package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.AgentToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolAgent
import de.heckenmann.visualagent.agent.tools.api.ToolAgentConfig
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.beans.factory.ObjectProvider

/**
 * Creates a new sub-agent from a template.
 */
@AgentTool
class AgentCreateTool(
    private val agents: AgentToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:create"),
            name = ToolId("agent:create").toFunctionName(),
            description =
                "Create a new sub-agent. " +
                    "Input: {\"name\":\"Coder\",\"role\":\"Implementation\",\"templateName\":\"coder\"}. " +
                    "templateName selects the tool set: researcher (read/search), coder (write/terminal), analyst (analysis). " +
                    "Returns the new agent ID and its assigned tools.",
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
        val created = agents.create(name, role, templateName)
        val tools = agents.providerToolNames(created.id)
        val toolHint =
            buildString {
                appendLine("Created agent ${created.id} ($name, template=$templateName)")
                appendLine("Assigned tools: [${tools.joinToString(",")}]")
                append("To assign work, create a todo with \"assignedAgentId\": \"${created.id}\".")
            }
        return success("agent:create", toolHint)
    }
}

/**
 * Updates an existing sub-agent.
 *
 * Accepts full AgentConfig fields and merges them over the existing configuration.
 */
@AgentTool
class AgentUpdateTool(
    private val agents: AgentToolPort,
    private val registry: ObjectProvider<ToolRegistry>,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:update"),
            name = ToolId("agent:update").toFunctionName(),
            description =
                "Update an existing sub-agent's configuration. " +
                    "Input fields: id (required), name, role, timeout, maxRetries, memoryLimitMb, provider, " +
                    "model, temperature, topP, maxTokens, variant, options, tools, and templateName (optional). " +
                    "For tools, use provider function names from an available agent inventory. " +
                    "Unknown function names are rejected. Only provided fields are updated.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val id = input.requiredString("id")
        val agent = agents.get(id) ?: return failure("agent:update", "Agent not found")
        val templateName = input.string("templateName")?.takeIf(String::isNotBlank)
        val baseConfig = templateName?.let(agents::template) ?: agent.config
        val requestedTools =
            input.jsonObject["tools"]?.let { value ->
                value as? JsonArray ?: return failure("agent:update", "TOOL_ARGUMENTS: Tools must be an array of function names")
            }
        val toolIdsByFunctionName =
            if (requestedTools != null) {
                registry.getObject().toolDefinitions().associate { it.name to it.id.value }
            } else {
                emptyMap()
            }
        val tools =
            requestedTools?.map { entry ->
                val name =
                    (entry as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?: return failure("agent:update", "TOOL_ARGUMENTS: Tool function names must be strings")
                toolIdsByFunctionName[name]
                    ?: return failure(
                        "agent:update",
                        "TOOL_ARGUMENTS: Unknown tool function '$name'. Use a name from the agent's available tool inventory.",
                    )
            }
        val config = mergeConfigFromInput(baseConfig, input, tools)
        val updated = agents.update(id, input.string("name"), input.string("role"), config)
        return if (updated) {
            agents.get(id) ?: return failure("agent:update", "Agent not found")
            val tools = agents.providerToolNames(id)
            val toolHint =
                buildString {
                    appendLine("Updated agent $id")
                    appendLine("Assigned tools: [${tools.joinToString(",")}]")
                    append("To assign work, create a todo with \"assignedAgentId\": \"$id\".")
                }
            success("agent:update", toolHint)
        } else {
            failure("agent:update", "Agent not found")
        }
    }

    private fun mergeConfigFromInput(
        base: ToolAgentConfig,
        input: JsonObject,
        tools: List<String>?,
    ): ToolAgentConfig {
        val options = input.jsonObject["options"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: base.options
        return base.copy(
            timeout = input.int("timeout") ?: base.timeout,
            maxRetries = input.int("maxRetries") ?: base.maxRetries,
            memoryLimitMb = input.int("memoryLimitMb")?.toLong() ?: base.memoryLimitMb,
            provider = input.primitiveString("provider") ?: base.provider,
            model = input.primitiveString("model") ?: base.model,
            temperature = input.primitiveString("temperature")?.toDoubleOrNull() ?: base.temperature,
            topP = input.primitiveString("topP")?.toDoubleOrNull() ?: base.topP,
            maxTokens = input.int("maxTokens") ?: base.maxTokens,
            variant = input.primitiveString("variant") ?: base.variant,
            options = options,
            tools = tools ?: base.tools,
            templateName = input.primitiveString("templateName") ?: base.templateName,
        )
    }

    private fun JsonObject.primitiveString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

/**
 * Deletes a sub-agent.
 */
@AgentTool
class AgentDeleteTool(
    private val agents: AgentToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:delete"),
            name = ToolId("agent:delete").toFunctionName(),
            description =
                "Delete a sub-agent. " +
                    "Input: {\"id\":\"123\"}. " +
                    "Permanently removes the agent. Any todos assigned to this agent will need reassignment.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val id = parseObject(inputJson).requiredString("id")
        return if (agents.delete(id)) {
            success("agent:delete", "Deleted agent $id")
        } else {
            failure("agent:delete", "Agent not found")
        }
    }
}

/**
 * Reads the persisted work log for a sub-agent.
 */
@AgentTool
class AgentLogTool(
    private val agents: AgentToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:log"),
            name = ToolId("agent:log").toFunctionName(),
            description =
                "Read the persisted work log for a sub-agent. " +
                    "Input: {\"id\":\"123\"}. " +
                    "Returns up to 50 recent log entries with timestamps.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val id = parseObject(inputJson).requiredString("id")
        if (agents.get(id) == null) {
            return failure("agent:log", "Agent not found")
        }
        val logs = agents.logs(id, 50)
        if (logs.isEmpty()) return success("agent:log", "No log entries for agent $id.")
        val text = logs.joinToString("\n") { "- ${it.createdAt}: ${it.content.take(500)}" }
        return success("agent:log", "Work log for agent $id:\n$text")
    }
}

/**
 * Shows full details for a single sub-agent, including tools, model, current task, and recent log.
 */
@AgentTool
class AgentShowTool(
    private val agents: AgentToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:show"),
            name = ToolId("agent:show").toFunctionName(),
            description =
                "Show full details for one sub-agent including tools, model, status, current task, and recent log. " +
                    "Input: {\"id\":\"123\"}. " +
                    "Use this to inspect an agent's capabilities before assigning work.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val id = parseObject(inputJson).requiredString("id")
        val agent = agents.get(id) ?: return failure("agent:show", "Agent not found")
        val details = formatAgentDetails(agent)
        return success("agent:show", details)
    }

    private fun formatAgentDetails(agent: ToolAgent): String {
        val tools = agents.providerToolNames(agent.id)
        val configId = agents.configId(agent.id)
        val description = configId?.let(agents::configDescription).orEmpty()
        val model = agent.config.model?.ifBlank { null } ?: "inherited"
        val template = configId ?: agent.config.model?.ifBlank { null } ?: "inherited"
        val currentWork =
            when {
                agent.status == "BUSY" && agent.currentTodoId != null ->
                    "Currently working on todo ${agent.currentTodoId}: ${agent.currentTask?.take(120) ?: "unknown task"}"
                else -> "Currently idle"
            }
        val logLines =
            agents
                .logs(agent.id, 10)
                .joinToString("\n") { "- ${it.createdAt}: ${it.content.take(500)}" }
                .ifBlank { "- no log entries yet" }
        return buildString {
            appendLine("Agent ${agent.id} | ${agent.name} | ${agent.role}")
            appendLine("Status: ${agent.status}")
            appendLine("Model: $model")
            appendLine("Template/Config: $template")
            if (description.isNotBlank()) appendLine("Description: $description")
            appendLine("Tools: [${tools.joinToString(",")}]")
            appendLine(currentWork)
            appendLine("Recent work log:")
            append(logLines)
        }
    }
}

private fun AgentToolPort.providerToolNames(id: String): List<String> = tools(id).map { ToolId(it).toFunctionName() }.sorted()
