package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Looks up PTR records for one numeric IPv4 or IPv6 address from the Visual Agent server. */
@AgentTool
class NetworkReverseDnsTool(
    private val resolver: HostResolver,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Perform a reverse DNS PTR lookup for one numeric IPv4 or IPv6 address from the Visual Agent server. " +
                    "Optionally select a DNS server IP and port. Input: {\"address\":\"192.0.2.53\",\"dnsServer\":\"192.0.2.1\"}.",
            inputSchema =
                """{"type":"object","properties":{"address":{"type":"string"},"dnsServer":{"type":"string"},"dnsPort":{"type":"integer","minimum":1,"maximum":65535}},"required":["address"],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val address =
            runCatching { input.requiredString("address").toNumericIpAddress() }
                .getOrElse { return failure(TOOL_ID.value, "Invalid address. Reverse lookup requires an IPv4 or IPv6 literal.") }
        val server =
            runCatching { parseDnsServerInput(input) }
                .getOrElse {
                    return failure(
                        TOOL_ID.value,
                        "Invalid dnsServer or dnsPort. Use a DNS server IP and a port from 1 to 65535.",
                    )
                }
        val names =
            try {
                resolver
                    .reverse(address, server)
                    .map { it.trimEnd('.').lowercase() }
                    .filter(String::isNotBlank)
                    .distinct()
                    .sorted()
            } catch (_: Exception) {
                return failure(TOOL_ID.value, "Reverse DNS lookup failed on the Visual Agent server.")
            }
        val truncated = names.size > MAX_NAMES
        val data =
            buildJsonObject {
                put("queriedAddress", address.hostAddress)
                put("dnsServer", server?.description() ?: "system")
                putJsonArray("ptrNames") { names.take(MAX_NAMES).forEach { add(JsonPrimitive(it)) } }
                put("truncated", truncated)
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("network:reverse-dns")
        const val MAX_NAMES = 32
    }
}
