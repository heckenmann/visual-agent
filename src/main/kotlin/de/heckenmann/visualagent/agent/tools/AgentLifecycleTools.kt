package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 *
 * Accepts full AgentConfig fields and merges them over the existing configuration.
 */
@Component
class AgentUpdateTool(
    @param:Lazy private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:update"),
            name = ToolId("agent:update").toFunctionName(),
            description =
                "Update a sub-agent. Input: {\"id\":\"123\",\"name\":\"Coder\",\"role\":\"Implementation\"," +
                    "\"timeout\":120,\"maxRetries\":3,\"memoryLimitMb\":1024,\"provider\":\"ollama\",\"model\":\"llama3\"," +
                    "\"temperature\":0.7,\"topP\":0.9,\"maxTokens\":4096,\"variant\":\"chat\"," +
                    "\"options\":{\"seed\":\"42\"},\"tools\":[\"file:read\",\"terminal\"],\"templateName\":\"coder\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val id = input.requiredString("id")
        val agent = agentManager.getSubAgent(id) ?: return failure("agent:update", "Agent not found")
        val templateName = input.string("templateName")?.takeIf(String::isNotBlank)
        val baseConfig = templateName?.let(AgentConfig::fromTemplate) ?: agent.config
        val config = mergeConfigFromInput(baseConfig, input)
        val updated = agentManager.updateAgent(id, input.string("name"), input.string("role"), config)
        return if (updated) success("agent:update", "Updated agent $id") else failure("agent:update", "Agent not found")
    }

    private fun mergeConfigFromInput(
        base: AgentConfig,
        input: JsonObject,
    ): AgentConfig {
        val options = input.jsonObject["options"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: base.options
        val tools =
            input.jsonObject["tools"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: base.tools
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
            tools = tools,
        )
    }

    private fun JsonObject.primitiveString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
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

/**
 * Reads the persisted work log for a sub-agent.
 */
@Component
class AgentLogTool(
    @param:Lazy private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("agent:log"),
            name = ToolId("agent:log").toFunctionName(),
            description = "Read the persisted work log for a sub-agent. Input: {\"id\":\"123\"}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val id = parseObject(inputJson).requiredString("id")
        if (agentManager.getSubAgent(id) == null) {
            return failure("agent:log", "Agent not found")
        }
        val logs = agentManager.memoryStore.searchMemories("agent:$id:log", limit = 50)
        if (logs.isEmpty()) return success("agent:log", "No log entries for agent $id.")
        val text = logs.joinToString("\n") { "- ${it.createdAt}: ${it.content.take(500)}" }
        return success("agent:log", "Work log for agent $id:\n$text")
    }
}
