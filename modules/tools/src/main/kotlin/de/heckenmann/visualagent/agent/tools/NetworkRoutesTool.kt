package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Lists bounded server route tables using fixed platform commands. */
@AgentTool
class NetworkRoutesTool(
    private val probe: RouteTableProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect the Visual Agent server's IPv4 and IPv6 routes, including normalized destinations, default gateways, " +
                    "interfaces, and metrics where reported. Raw output is bounded and commands are fixed; " +
                    "no host, interface, or command can be supplied. Input: {\"family\":\"all\"}.",
            inputSchema =
                """{"type":"object","properties":{"family":{"type":"string","enum":["all","ipv4","ipv6"]}},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = runCatching { parseObject(inputJson) }.getOrElse { return failure(TOOL_ID.value, "Input must be a JSON object.") }
        if (input.keys.any { it != "family" }) return failure(TOOL_ID.value, "Only the family field is supported.")
        val family = input.string("family")?.lowercase() ?: DEFAULT_FAMILY
        if (family !in ROUTE_FAMILIES) return failure(TOOL_ID.value, "Invalid family. Use all, ipv4, or ipv6.")
        val results =
            runCatching { probe.snapshot(family) }
                .getOrElse { return failure(TOOL_ID.value, "Server route tables are unavailable.") }
        val data =
            buildJsonObject {
                put("hostScope", "visual-agent-server")
                putJsonArray("tables") {
                    results.forEach { result ->
                        add(
                            buildJsonObject {
                                put("family", result.family)
                                put("status", result.status)
                                putJsonArray("routes") {
                                    result.routes.forEach { route ->
                                        add(
                                            buildJsonObject {
                                                put("destination", route.destination)
                                                put("gateway", route.gateway)
                                                put("interface", route.networkInterface)
                                                route.metric?.let { put("metric", it) }
                                                put("defaultRoute", route.defaultRoute)
                                            },
                                        )
                                    }
                                }
                                put("output", result.output)
                                put("truncated", result.truncated)
                            },
                        )
                    }
                }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("network:routes")
        val ROUTE_FAMILIES = setOf("all", "ipv4", "ipv6")
        const val DEFAULT_FAMILY = "all"
    }
}

/** Bounded result for one address-family route table. */
data class RouteTableResult(
    /** Address family requested from the host. */
    val family: String,
    /** Normalized execution outcome. */
    val status: String,
    /** Bounded native route-table text, empty on execution failure. */
    val output: String,
    /** Whether output reached its capture limit. */
    val truncated: Boolean,
    /** Parsed normalized routes, empty when the native output is unsupported or incomplete. */
    val routes: List<NormalizedRoute> = emptyList(),
)

/** Provides fixed, bounded server-side route-table queries. */
fun interface RouteTableProbe {
    /** Reads route tables for `all`, `ipv4`, or `ipv6`. */
    fun snapshot(family: String): List<RouteTableResult>
}

/** Platform-neutral route data extracted from native route-table output. */
data class NormalizedRoute(
    /** Destination prefix, normalized to CIDR notation when available. */
    val destination: String,
    /** Next-hop address, or `on-link` for directly connected routes. */
    val gateway: String,
    /** Name of the route's outgoing network interface when reported. */
    val networkInterface: String,
    /** Route preference metric when reported by the operating system. */
    val metric: Long? = null,
    /** Whether this route is the default route for its address family. */
    val defaultRoute: Boolean,
)

/** Executes platform-selected route-table commands without passing model input to the process. */
@Component
class JvmRouteTableProbe(
    private val processRunner: DiagnosticProcessRunner,
    @Value("\${os.name}") private val osName: String,
) : RouteTableProbe {
    override fun snapshot(family: String): List<RouteTableResult> {
        require(family in FAMILIES)
        val platform = RouteTablePlatform.fromOsName(osName) ?: return listOf(unsupported(family))
        return RouteTableCommandBuilder.commands(platform, family).map { (addressFamily, command) ->
            val result = processRunner.run(command, PROCESS_TIMEOUT_MILLIS, MAX_OUTPUT_CHARACTERS)
            val status =
                when {
                    result.cancelled -> "cancelled"
                    result.timedOut -> "deadline_exceeded"
                    result.executableUnavailable -> "executable_unavailable"
                    result.exitCode == 0 -> AVAILABLE
                    else -> "process_failed"
                }
            RouteTableResult(
                family = addressFamily,
                status = status,
                routes = if (status == AVAILABLE) RouteTableOutputParser.parse(platform, addressFamily, result.output) else emptyList(),
                output = if (status == AVAILABLE) result.output else "",
                truncated = result.output.length >= MAX_OUTPUT_CHARACTERS,
            )
        }
    }

    private fun unsupported(family: String) = RouteTableResult(family, "unsupported_platform", "", false)

    private companion object {
        val FAMILIES = setOf("all", "ipv4", "ipv6")
        const val AVAILABLE = "available"
        const val MAX_OUTPUT_CHARACTERS = 12_000
        const val PROCESS_TIMEOUT_MILLIS = 5_000L
    }
}
