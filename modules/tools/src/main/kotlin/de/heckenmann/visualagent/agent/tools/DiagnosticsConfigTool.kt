package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerConfigurationDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Reports the sanitized effective server and active model configuration. */
@AgentTool
class DiagnosticsConfigTool(
    private val configuration: ServerConfigurationDiagnosticsPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Show sanitized effective Visual Agent server configuration and validate the active provider/model selection. " +
                    "Secrets, local filesystem paths, endpoint paths, and user instructions are never returned. Input: {}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val snapshot =
            runCatching { configuration.snapshot() }
                .getOrElse { return failure(TOOL_ID.value, "Effective server configuration is unavailable.") }
        val provider = snapshot.provider
        val warnings = snapshot.warnings.distinct().sorted()
        val data =
            buildJsonObject {
                put("hostScope", "visual-agent-server")
                put("valid", warnings.isEmpty())
                put("providerId", provider.providerId)
                provider.adapter?.let { put("providerAdapter", it) }
                put("providerEnabled", provider.enabled)
                provider.endpointOrigin?.let { put("endpointOrigin", it) }
                put("apiKeyConfigured", provider.apiKeyConfigured)
                put("modelId", provider.modelId)
                provider.modelContextLimit?.let { put("modelContextLimit", it) }
                provider.modelOutputLimit?.let { put("modelOutputLimit", it) }
                putJsonArray("modelCapabilities") { provider.modelCapabilities.sorted().forEach { add(JsonPrimitive(it)) } }
                put("modelCapabilitiesComplete", provider.modelCapabilitiesComplete)
                put("configuredContextWindow", snapshot.contextWindow)
                put("timeoutSeconds", snapshot.timeoutSeconds)
                put("maxParallelSubAgents", snapshot.maxParallelSubAgents)
                putJsonArray("warnings") { warnings.forEach { add(JsonPrimitive(it)) } }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("diagnostics:config")
    }
}
