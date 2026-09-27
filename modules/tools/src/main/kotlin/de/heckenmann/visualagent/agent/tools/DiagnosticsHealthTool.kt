package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerHealthDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerHealthDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import reactor.core.publisher.Mono

/** Reports a bounded aggregate status for the core server subsystems. */
@AgentTool
class DiagnosticsHealthTool(
    private val diagnostics: ServerHealthDiagnosticsPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Summarize Visual Agent server health for its database, active provider, and effective configuration. " +
                    "Returns statuses and stable non-sensitive reason codes, never credentials, paths, or application data. Input: {}.",
            inputSchema = """{"type":"object","properties":{},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        executeReactive(inputJson, context).block()
            ?: failure(TOOL_ID.value, "Health diagnostics are unavailable.")

    override fun executeReactive(
        inputJson: String,
        context: Map<String, Any>,
    ): Mono<ToolResult> {
        val input =
            runCatching { parseObject(inputJson) }.getOrElse {
                return Mono.just(failure(TOOL_ID.value, "Input must be an empty JSON object."))
            }
        if (input.isNotEmpty()) return Mono.just(failure(TOOL_ID.value, "Input must be an empty JSON object."))
        return diagnostics
            .snapshot()
            .map(::result)
            .onErrorReturn(failure(TOOL_ID.value, "Health diagnostics are unavailable."))
    }

    private fun result(snapshot: ServerHealthDiagnostic): ToolResult {
        val data =
            buildJsonObject {
                put("hostScope", "visual-agent-server")
                put("status", snapshot.status)
                put("database", snapshot.database)
                putJsonArray("databaseReasons") { snapshot.databaseReasons.forEach { add(JsonPrimitive(it)) } }
                put("provider", snapshot.provider)
                putJsonArray("providerReasons") { snapshot.providerReasons.forEach { add(JsonPrimitive(it)) } }
                put("configuration", snapshot.configuration)
                putJsonArray("configurationReasons") { snapshot.configurationReasons.forEach { add(JsonPrimitive(it)) } }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("diagnostics:health")
    }
}
