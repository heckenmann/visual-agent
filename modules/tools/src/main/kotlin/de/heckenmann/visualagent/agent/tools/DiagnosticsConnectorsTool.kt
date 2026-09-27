package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerConnectorDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Reports connector diagnostic availability without guessing at an unimplemented framework. */
@AgentTool
class DiagnosticsConnectorsTool(
    private val diagnostics: ServerConnectorDiagnosticsPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect whether the Visual Agent connector diagnostics framework is available. " +
                    "This read-only tool never returns connector credentials. Input: {}.",
            inputSchema = """{"type":"object","properties":{},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input =
            runCatching {
                parseObject(
                    inputJson,
                )
            }.getOrElse { return failure(TOOL_ID.value, "Input must be an empty JSON object.") }
        if (input.isNotEmpty()) return failure(TOOL_ID.value, "Input must be an empty JSON object.")
        val snapshot =
            runCatching { diagnostics.snapshot() }
                .getOrElse { return failure(TOOL_ID.value, "Connector diagnostics are unavailable.") }
        val data =
            buildJsonObject {
                put("hostScope", "visual-agent-server")
                put("status", if (snapshot.available) "available" else "not_available")
                snapshot.configuredCount?.let { put("configuredCount", it) }
                snapshot.reason?.let { put("reason", it) }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("diagnostics:connectors")
    }
}
