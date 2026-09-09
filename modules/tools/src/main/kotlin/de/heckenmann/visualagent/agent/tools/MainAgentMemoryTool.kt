package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.MainAgentMemoryEditResult
import de.heckenmann.visualagent.agent.tools.api.MainAgentMemoryPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Lets the main model inspect and replace its durable long-term reference document. */
@AgentTool
class MainAgentMemoryTool(
    private val memory: MainAgentMemoryPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("memory"),
            name = ToolId("memory").toFunctionName(),
            description =
                "Read or replace your durable long-term memory. " +
                    "Use show: {\"action\":\"show\"}. " +
                    "Use edit: {\"action\":\"edit\",\"content\":\"complete replacement\",\"expectedRevision\":7}. " +
                    "Keep it concise, durable, and free of secrets. A stale edit returns the current revision.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return when (input.string("action") ?: "show") {
            "show" -> snapshotResult(memory.show())
            "edit" ->
                runCatching {
                    edit(
                        input.requiredString("content"),
                        input["expectedRevision"]?.jsonPrimitive?.longOrNull ?: error("expectedRevision must be a number"),
                    )
                }.getOrElse { error -> failure("memory", error.message ?: "Invalid memory edit request") }
            else -> failure("memory", "Unsupported memory action. Use show or edit.")
        }
    }

    private fun edit(
        content: String,
        expectedRevision: Long,
    ): ToolResult =
        when (val result = memory.edit(content, expectedRevision)) {
            is MainAgentMemoryEditResult.Saved -> snapshotResult(result.snapshot)
            is MainAgentMemoryEditResult.Conflict ->
                ToolResult(
                    toolId = "memory",
                    success = false,
                    content = "",
                    error = "Memory revision is stale. Read the current memory before retrying.",
                    data = Json.encodeToJsonElement(result.snapshot),
                )
        }

    private fun snapshotResult(snapshot: de.heckenmann.visualagent.agent.tools.api.MainAgentMemorySnapshot): ToolResult =
        ToolResult(
            toolId = "memory",
            success = true,
            content = Json.encodeToString(snapshot),
            data = Json.encodeToJsonElement(snapshot),
        )
}
