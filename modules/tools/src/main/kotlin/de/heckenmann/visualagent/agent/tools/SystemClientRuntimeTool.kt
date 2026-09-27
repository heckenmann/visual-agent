package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Requests and returns the desktop client's JVM snapshot only when explicitly called. */
@AgentTool
class SystemClientRuntimeTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Ask the desktop client for its current JVM and OS metrics. This is not the server runtime. Input: {}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val requester =
            context[ClientDataRequester.METADATA_KEY] as? ClientDataRequester
                ?: return failure(TOOL_ID.value, "The server has no request-scoped access to client diagnostics.")
        val snapshot =
            requester.requestRuntimeReport()
                ?: return failure(TOOL_ID.value, "The client could not provide JVM diagnostics when requested.")
        if (snapshot.processId <= 0 ||
            snapshot.availableProcessors !in 1..MAX_PROCESSORS ||
            snapshot.uptimeMillis < 0 ||
            snapshot.heapUsedBytes < 0 ||
            snapshot.heapCommittedBytes < snapshot.heapUsedBytes
        ) {
            return failure(TOOL_ID.value, "Client JVM diagnostics are invalid or incomplete.")
        }
        val data =
            buildJsonObject {
                put("scope", "desktop-client-jvm")
                put("processId", snapshot.processId)
                put("osName", snapshot.osName.safeDescriptor())
                put("osVersion", snapshot.osVersion.safeDescriptor())
                put("architecture", snapshot.architecture.safeDescriptor())
                put("availableProcessors", snapshot.availableProcessors)
                put("javaVersion", snapshot.javaVersion.safeDescriptor())
                put("jvmVendor", snapshot.jvmVendor.safeDescriptor())
                put("vmName", snapshot.vmName.safeDescriptor())
                put("uptimeMillis", snapshot.uptimeMillis.coerceAtLeast(0))
                put("heapUsedBytes", snapshot.heapUsedBytes)
                put("heapCommittedBytes", snapshot.heapCommittedBytes)
                snapshot.heapMaxBytes?.takeIf { it >= 0 }?.let { put("heapMaxBytes", it) }
                snapshot.totalPhysicalMemoryBytes?.takeIf { it >= 0 }?.let { put("totalPhysicalMemoryBytes", it) }
                snapshot.freePhysicalMemoryBytes?.takeIf { it >= 0 }?.let { put("freePhysicalMemoryBytes", it) }
                snapshot.processCpuLoad?.takeIf { it.isFinite() && it in 0.0..1.0 }?.let { put("processCpuLoad", it) }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private fun String.safeDescriptor(): String =
        filter {
            it.isLetterOrDigit() || it in SAFE_DESCRIPTOR_PUNCTUATION
        }.take(MAX_DESCRIPTOR_LENGTH)

    private companion object {
        val TOOL_ID = ToolId("system:client-runtime")
        const val MAX_PROCESSORS = 1024
        const val MAX_DESCRIPTOR_LENGTH = 96
        const val SAFE_DESCRIPTOR_PUNCTUATION = " ._()+-/,:"
    }
}
