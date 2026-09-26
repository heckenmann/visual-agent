package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Checks server-side host reachability using the JVM network API. */
@AgentTool
class NetworkPingTool(
    private val probe: PingProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Run a bounded reachability check from the Visual Agent server. The JVM may use ICMP or a platform-specific fallback; " +
                    "a missing response does not prove a host is down. Input: {\"host\":\"example.org\",\"family\":\"auto\",\"count\":4,\"timeoutSeconds\":5}.",
            inputSchema =
                """{"type":"object","properties":{"host":{"type":"string"},"family":{"type":"string","enum":["auto","ipv4","ipv6"]},"count":{"type":"integer","minimum":1,"maximum":10},"timeoutSeconds":{"type":"integer","minimum":1,"maximum":30}},"required":["host"],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val host =
            runCatching { normalizeNetworkHost(input.requiredString("host")) }
                .getOrElse { return failure(TOOL_ID.value, "Invalid host. Supply a hostname or IP address, not a URL or path.") }
        val family = input.string("family")?.lowercase() ?: DEFAULT_FAMILY
        if (family !in ADDRESS_FAMILIES) return failure(TOOL_ID.value, "Invalid family. Use auto, ipv4, or ipv6.")
        val count = input.int("count") ?: DEFAULT_COUNT
        if (count !in MIN_COUNT..MAX_COUNT) return failure(TOOL_ID.value, "Invalid count. Use a value from 1 to $MAX_COUNT.")
        val timeoutSeconds = input.int("timeoutSeconds") ?: DEFAULT_TIMEOUT_SECONDS
        if (timeoutSeconds !in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) {
            return failure(TOOL_ID.value, "Invalid timeoutSeconds. Use a value from 1 to $MAX_TIMEOUT_SECONDS.")
        }

        val result = probe.ping(host, family, count, timeoutSeconds)
        val data =
            buildJsonObject {
                put("host", host)
                put("family", family)
                put("reachabilityMethod", "JVM InetAddress.isReachable; implementation may use ICMP or a TCP echo fallback")
                put("status", result.status)
                result.resolvedAddress?.let { put("resolvedAddress", it) }
                put("attemptsRequested", count)
                put("attemptsCompleted", result.attemptsCompleted)
                put("reachableResponses", result.reachableResponses)
                result.unansweredPercent?.let { put("unansweredAttemptsPercent", it) }
                result.minRttMillis?.let { put("minRttMillis", it) }
                result.averageRttMillis?.let { put("averageRttMillis", it) }
                result.maxRttMillis?.let { put("maxRttMillis", it) }
                put("elapsedMillis", result.elapsedMillis)
                put("deadlineReached", result.deadlineReached)
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("network:ping")
        val ADDRESS_FAMILIES = setOf("auto", "ipv4", "ipv6")
        const val DEFAULT_FAMILY = "auto"
        const val DEFAULT_COUNT = 4
        const val DEFAULT_TIMEOUT_SECONDS = 5
        const val MIN_COUNT = 1
        const val MAX_COUNT = 10
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 30
    }
}

/** Runs bounded reachability attempts through a replaceable implementation. */
fun interface PingProbe {
    /** Check one validated host with the selected address family, count, and overall timeout. */
    fun ping(
        host: String,
        family: String,
        count: Int,
        timeoutSeconds: Int,
    ): PingProbeResult
}

/** Aggregate result from JVM reachability attempts. */
data class PingProbeResult(
    /** Stable state such as `completed`, `dns_failure`, or `family_unavailable`. */
    val status: String,
    /** Resolved target selected for the attempts. */
    val resolvedAddress: String?,
    /** Number of reachability calls that completed. */
    val attemptsCompleted: Int,
    /** Number of calls reporting that the address is reachable. */
    val reachableResponses: Int,
    /** Percentage of completed calls that did not report reachability. */
    val unansweredPercent: Double?,
    /** Minimum response time among successful calls, in milliseconds. */
    val minRttMillis: Double?,
    /** Average response time among successful calls, in milliseconds. */
    val averageRttMillis: Double?,
    /** Maximum response time among successful calls, in milliseconds. */
    val maxRttMillis: Double?,
    /** Total elapsed time, in milliseconds. */
    val elapsedMillis: Long,
    /** Whether the overall deadline stopped the requested attempts early. */
    val deadlineReached: Boolean,
)

/** Isolated call to the JVM reachability API. */
fun interface ReachabilityChecker {
    /** Check whether [address] is reachable within [timeoutMillis]. */
    fun isReachable(
        address: java.net.InetAddress,
        timeoutMillis: Int,
    ): Boolean
}

/** Monotonic nanosecond source, replaceable for deterministic timeout tests. */
fun interface MonotonicTimeSource {
    /** Return a monotonic timestamp in nanoseconds. */
    fun nanoTime(): Long
}
