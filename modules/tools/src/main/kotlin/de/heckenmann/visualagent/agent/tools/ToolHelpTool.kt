package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonObject

/** Provides request-scoped tool discovery and execution when full schemas do not fit. */
class ToolHelpTool(
    private val registry: () -> ToolRegistry,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description =
                "Discover tools available to this agent. Use action=list to list them, action=show with a function name " +
                    "to inspect its description and JSON schema, or action=call with name and arguments to invoke it.",
            inputSchema =
                """{"type":"object","properties":{"action":{"type":"string","enum":["list","show","call"]},"name":{"type":"string"},"arguments":{"type":"object","additionalProperties":true}},"required":["action"],"additionalProperties":false}""",
        )

    /** Lists, documents, or invokes a tool permitted in the current request. */
    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val availableTools = availableTools(context)
        return when (input.string("action")?.lowercase()) {
            "list" ->
                success(
                    TOOL_ID,
                    if (availableTools.isEmpty()) {
                        "No tools are enabled for this agent."
                    } else {
                        "Available tools:\n" +
                            availableTools.joinToString("\n") { tool -> "- ${tool.name}: ${tool.description.lineSequence().first()}" } +
                            "\nTo inspect a tool, call tool_help with {\"action\":\"show\",\"name\":\"<function_name>\"}. " +
                            "To invoke one, call tool_help with {\"action\":\"call\",\"name\":\"<function_name>\",\"arguments\":{}}."
                    },
                )
            "show" -> show(input.string("name"), availableTools)
            "call" -> invoke(input, context, availableTools)
            else -> failure(TOOL_ID, "Unsupported action. Use list, show, or call.")
        }
    }

    private fun show(
        name: String?,
        availableTools: List<ToolDefinition>,
    ): ToolResult {
        val tool =
            availableTools.firstOrNull { it.name == name }
                ?: return failure(TOOL_ID, "Tool not available to this agent: ${name.orEmpty()}")
        return success(
            TOOL_ID,
            "${tool.name}: ${tool.description}\nInput schema:\n${tool.inputSchema}",
        )
    }

    private fun invoke(
        input: JsonObject,
        context: Map<String, Any>,
        availableTools: List<ToolDefinition>,
    ): ToolResult {
        val functionName =
            input.string("name")
                ?: return failure(TOOL_ID, "The call action requires a function name.")
        val definition =
            availableTools.firstOrNull { it.name == functionName }
                ?: return failure(TOOL_ID, "Tool not available to this agent: $functionName")
        val arguments =
            input["arguments"] as? JsonObject
                ?: return failure(TOOL_ID, "The call action requires arguments to be a JSON object.")
        val enabledIds = context.enabledToolIds()
        val tool =
            registry().resolve(enabledIds).firstOrNull { it.definition.id == definition.id }
                ?: return failure(TOOL_ID, "Tool not available to this agent: $functionName")
        val nestedContext = context + ("toolHelpDelegated" to true)
        val result = registry().executeBlockingResult(tool, arguments.toString(), nestedContext)
        return ToolResult(TOOL_ID, result.success, result.content, result.error, result.data)
    }

    private fun availableTools(context: Map<String, Any>): List<ToolDefinition> {
        val allowedIds = context.enabledToolIds()
        return registry()
            .resolve(allowedIds)
            .map(registry()::definition)
            .sortedBy(ToolDefinition::name)
    }

    private fun Map<String, Any>.enabledToolIds(): Set<ToolId> =
        (this["enabledTools"] as? Set<*>)
            .orEmpty()
            .filterIsInstance<String>()
            .filterNot { it == TOOL_ID }
            .mapTo(linkedSetOf(), ::ToolId)

    private fun JsonObject.string(key: String): String? = this[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }

    private companion object {
        const val TOOL_ID = "tool:help"
    }
}
