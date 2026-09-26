package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Tests one TCP connection from the Visual Agent server to one explicit host and port. */
@AgentTool
class NetworkTcpTool(
    private val probe: TcpConnectionProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Check one TCP connection from the Visual Agent server to a host and port. " +
                    "This does not scan port ranges. family may be auto, ipv4, or ipv6. " +
                    "Input: {\"host\":\"example.org\",\"port\":443,\"family\":\"auto\"}.",
            inputSchema =
                """{"type":"object","properties":{"host":{"type":"string"},"port":{"type":"integer","minimum":1,"maximum":65535},"family":{"type":"string","enum":["auto","ipv4","ipv6"]}},"required":["host","port"],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val host =
            runCatching { normalizeNetworkHost(input.requiredString("host")) }
                .getOrElse { return failure(TOOL_ID.value, "Invalid host. Supply a hostname or IP address, not a URL or path.") }
        val port = input.int("port") ?: return failure(TOOL_ID.value, "Invalid port. Supply one port from 1 to 65535.")
        if (port !in 1..MAX_PORT) return failure(TOOL_ID.value, "Invalid port. Supply one port from 1 to 65535.")
        val family = input.string("family")?.lowercase() ?: "auto"
        if (family !in ADDRESS_FAMILIES) return failure(TOOL_ID.value, "Invalid family. Use auto, ipv4, or ipv6.")

        val outcome = probe.connect(host, port, family)
        val data =
            buildJsonObject {
                put("host", host)
                put("port", port)
                put("family", family)
                putJsonArray("resolvedAddresses") { outcome.resolvedAddresses.forEach { add(JsonPrimitive(it)) } }
                outcome.connectedAddress?.let { put("connectedAddress", it) }
                put("connected", outcome.connected)
                put("durationMillis", outcome.durationMillis)
                outcome.failureCategory?.let { put("failureCategory", it) }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("network:tcp")
        val ADDRESS_FAMILIES = setOf("auto", "ipv4", "ipv6")
        const val MAX_PORT = 65535
    }
}

/** Normalized result of one bounded TCP connection attempt. */
data class TcpProbeOutcome(
    /** All resolved candidate addresses considered for this single target. */
    val resolvedAddresses: List<String>,
    /** Address that accepted the connection, when successful. */
    val connectedAddress: String?,
    /** Whether a TCP connection was established. */
    val connected: Boolean,
    /** Total elapsed time in milliseconds. */
    val durationMillis: Long,
    /** Stable lower-case failure category, if no connection succeeded. */
    val failureCategory: String?,
)

/** Performs a single-host TCP diagnostic and normalizes connection outcomes. */
fun interface TcpConnectionProbe {
    /** Check one validated host, port, and address-family selection. */
    fun connect(
        host: String,
        port: Int,
        family: String,
    ): TcpProbeOutcome
}
