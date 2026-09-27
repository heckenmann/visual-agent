package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/** Resolves one hostname using the server's system resolver or an explicitly selected DNS server. */
@AgentTool
class NetworkDnsTool(
    private val resolver: HostResolver,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Resolve one hostname from the Visual Agent server. " +
                    "family may be ipv4, ipv6, or all (default). Optionally select a DNS server IP and port. " +
                    "Input: {\"host\":\"example.org\",\"family\":\"all\",\"dnsServer\":\"192.0.2.53\"}.",
            inputSchema =
                """{"type":"object","properties":{"host":{"type":"string"},"family":{"type":"string","enum":["ipv4","ipv6","all"]},"dnsServer":{"type":"string"},"dnsPort":{"type":"integer","minimum":1,"maximum":65535}},"required":["host"],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val host =
            runCatching { normalizeNetworkHost(input.requiredString("host")) }
                .getOrElse { return failure(TOOL_ID.value, "Invalid host. Supply a hostname or IP address, not a URL or path.") }
        val family = input.string("family")?.lowercase() ?: "all"
        if (family !in ADDRESS_FAMILIES) {
            return failure(TOOL_ID.value, "Invalid family. Use ipv4, ipv6, or all.")
        }
        val server =
            runCatching { parseDnsServerInput(input) }
                .getOrElse {
                    return failure(
                        TOOL_ID.value,
                        "Invalid dnsServer or dnsPort. Use a DNS server IP and a port from 1 to 65535.",
                    )
                }
        val addresses =
            try {
                resolver.resolve(host, server, family)
            } catch (_: UnknownHostException) {
                return failure(TOOL_ID.value, "The server could not resolve the requested host.")
            } catch (_: Exception) {
                return failure(TOOL_ID.value, "DNS resolution failed on the Visual Agent server.")
            }
        val ipv4 =
            if (family ==
                "ipv6"
            ) {
                emptyList()
            } else {
                addresses.filterIsInstance<Inet4Address>().distinctBy { it.hostAddress }.sortedBy { it.hostAddress }
            }
        val ipv6 =
            if (family ==
                "ipv4"
            ) {
                emptyList()
            } else {
                addresses.filterIsInstance<Inet6Address>().distinctBy { it.hostAddress }.sortedBy { it.hostAddress }
            }
        val truncated = ipv4.size > MAX_ADDRESSES || ipv6.size > MAX_ADDRESSES
        val data =
            buildJsonObject {
                put("queriedHost", host)
                put("family", family)
                put("dnsServer", server?.description() ?: "system")
                putJsonArray("ipv4Addresses") { ipv4.take(MAX_ADDRESSES).forEach { add(JsonPrimitive(it.hostAddress)) } }
                putJsonArray("ipv6Addresses") { ipv6.take(MAX_ADDRESSES).forEach { add(JsonPrimitive(it.hostAddress)) } }
                put("truncated", truncated)
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("network:dns")
        val ADDRESS_FAMILIES = setOf("ipv4", "ipv6", "all")
        const val MAX_ADDRESSES = 32
    }
}

internal fun parseDnsServerInput(input: JsonObject): DnsServerEndpoint? {
    val server = input.string("dnsServer")
    if (server == null) {
        require(input["dnsPort"] == null) { "dnsPort requires dnsServer" }
        return null
    }
    val port = if (input["dnsPort"] == null) DEFAULT_DNS_PORT else input.int("dnsPort") ?: error("dnsPort must be an integer")
    require(port in 1..MAX_DNS_PORT)
    return DnsServerEndpoint(server.toNumericIpAddress(), port)
}

internal fun DnsServerEndpoint.description(): String =
    if (address is Inet6Address) {
        "[${address.hostAddress}]:$port"
    } else {
        "${address.hostAddress}:$port"
    }

internal fun String.toNumericIpAddress(): InetAddress {
    val value = trim()
    if (':' in value) {
        require(value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it in ":.%_-" })
        return InetAddress.getByName(value).also { require(it is Inet6Address) }
    }
    val octets = value.split('.')
    require(octets.size == 4 && octets.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toInt() in 0..255 })
    return InetAddress.getByAddress(octets.map { it.toInt().toByte() }.toByteArray())
}

private const val DEFAULT_DNS_PORT = 53
private const val MAX_DNS_PORT = 65535
