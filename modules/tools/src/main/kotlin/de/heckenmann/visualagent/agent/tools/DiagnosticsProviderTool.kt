package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import reactor.core.publisher.Mono

/** Checks the active model provider without returning endpoint credentials or raw failures. */
@AgentTool
class DiagnosticsProviderTool(
    private val diagnostics: ServerProviderDiagnosticsPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Check whether the configured Visual Agent provider responds to a model-catalog request. " +
                    "This read-only check does not change the catalog or expose credentials or raw errors. Input: {}.",
            inputSchema = """{"type":"object","properties":{},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        executeReactive(inputJson, context).block()
            ?: failure(TOOL_ID.value, "Provider diagnostics are unavailable.")

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
            .check()
            .map(::result)
            .onErrorReturn(failure(TOOL_ID.value, "Provider diagnostics are unavailable."))
    }

    private fun result(snapshot: ServerProviderDiagnostic): ToolResult {
        val healthy =
            snapshot.enabled && snapshot.failureKind == null && snapshot.selectedModelAvailable == true
        val data =
            buildJsonObject {
                put("hostScope", "visual-agent-server")
                put("providerId", snapshot.providerId)
                put("adapter", snapshot.adapter)
                put("enabled", snapshot.enabled)
                put("credentialConfigured", snapshot.credentialConfigured)
                put("healthy", healthy)
                snapshot.discoveredModelCount?.let { put("discoveredModelCount", it) }
                snapshot.selectedModelAvailable?.let { put("selectedModelAvailable", it) }
                snapshot.failureKind?.let { put("failureKind", it) }
                snapshot.endpointOrigin?.let { put("endpointOrigin", it) }
                snapshot.selectedModelId?.let { put("selectedModelId", it) }
                snapshot.selectedModelCapabilities?.let { capabilities ->
                    putJsonArray("selectedModelCapabilities") { capabilities.forEach { add(it) } }
                }
                snapshot.selectedModelCapabilitiesComplete?.let { put("selectedModelCapabilitiesComplete", it) }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("diagnostics:provider")
    }
}
