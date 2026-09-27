package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/** Traces the server-side route to one explicit host with bounded native diagnostics. */
@AgentTool
class NetworkTracerouteTool(
    private val probe: TracerouteProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Trace the route from the Visual Agent server to one host. Intermediate routers may not respond. " +
                    "Input: {\"host\":\"example.org\",\"family\":\"auto\",\"maxHops\":20,\"timeoutSeconds\":30}.",
            inputSchema =
                """{"type":"object","properties":{"host":{"type":"string"},"family":{"type":"string","enum":["auto","ipv4","ipv6"]},"maxHops":{"type":"integer","minimum":1,"maximum":30},"timeoutSeconds":{"type":"integer","minimum":1,"maximum":60}},"required":["host"],"additionalProperties":false}""",
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
        val maxHops = input.int("maxHops") ?: DEFAULT_MAX_HOPS
        if (maxHops !in MIN_HOPS..MAX_HOPS) return failure(TOOL_ID.value, "Invalid maxHops. Use a value from 1 to $MAX_HOPS.")
        val timeoutSeconds = input.int("timeoutSeconds") ?: DEFAULT_TIMEOUT_SECONDS
        if (timeoutSeconds !in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) {
            return failure(TOOL_ID.value, "Invalid timeoutSeconds. Use a value from 1 to $MAX_TIMEOUT_SECONDS.")
        }

        val result = probe.trace(host, family, maxHops, timeoutSeconds)
        val data =
            buildJsonObject {
                put("host", host)
                put("family", family)
                put("status", result.status)
                result.resolvedAddress?.let { put("resolvedAddress", it) }
                put("destinationReached", result.destinationReached)
                put("elapsedMillis", result.elapsedMillis)
                put("truncatedOutput", result.truncatedOutput)
                putJsonArray("hops") {
                    result.hops.forEach { item ->
                        add(
                            buildJsonObject {
                                put("number", item.number)
                                put("status", item.status)
                                item.address?.let { put("address", it) }
                                putJsonArray("latenciesMillis") { item.latenciesMillis.forEach { add(JsonPrimitive(it)) } }
                            },
                        )
                    }
                }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("network:traceroute")
        val ADDRESS_FAMILIES = setOf("auto", "ipv4", "ipv6")
        const val DEFAULT_FAMILY = "auto"
        const val DEFAULT_MAX_HOPS = 20
        const val MAX_HOPS = 30
        const val MIN_HOPS = 1
        const val DEFAULT_TIMEOUT_SECONDS = 30
        const val MAX_TIMEOUT_SECONDS = 60
        const val MIN_TIMEOUT_SECONDS = 1
    }
}

/** One normalized route hop. */
data class TracerouteHop(
    /** One-based hop number. */
    val number: Int,
    /** Address returned by this hop, if any. */
    val address: String?,
    /** Parsed round-trip measurements from this hop. */
    val latenciesMillis: List<Double>,
    /** `responded` or `no_response`. */
    val status: String,
)

/** Normalized result from one bounded route trace. */
data class TracerouteResult(
    /** Stable result status such as `completed`, `deadline_exceeded`, or `dns_failure`. */
    val status: String,
    /** Numeric destination chosen by JVM name resolution. */
    val resolvedAddress: String?,
    /** Whether a hop reported the selected destination address. */
    val destinationReached: Boolean,
    /** Parsed hops returned by the platform utility. */
    val hops: List<TracerouteHop>,
    /** Elapsed wall-clock duration in milliseconds. */
    val elapsedMillis: Long,
    /** Whether the native tool output exceeded the capture limit. */
    val truncatedOutput: Boolean,
)

/** Performs one bounded route trace for [NetworkTracerouteTool]. */
fun interface TracerouteProbe {
    /** Trace [host] with the requested address family, hop ceiling, and total timeout. */
    fun trace(
        host: String,
        family: String,
        maxHops: Int,
        timeoutSeconds: Int,
    ): TracerouteResult
}

/** Adapts the OS traceroute utility to a bounded, normalized tool result. */
@Component
class JvmTracerouteProbe(
    private val resolver: HostResolver,
    private val processRunner: DiagnosticProcessRunner,
    @Value("\${os.name}") private val osName: String,
) : TracerouteProbe {
    override fun trace(
        host: String,
        family: String,
        maxHops: Int,
        timeoutSeconds: Int,
    ): TracerouteResult {
        val startedAt = System.nanoTime()
        val target =
            try {
                selectTarget(resolver.resolve(host, null), family)
            } catch (_: UnknownHostException) {
                return result("dns_failure", null, startedAt)
            } catch (_: Exception) {
                return result("dns_failure", null, startedAt)
            } ?: return result("family_unavailable", null, startedAt)
        val platform = TraceroutePlatform.fromOsName(osName) ?: return result("unsupported_platform", target.hostAddress, startedAt)
        val command = TracerouteCommandBuilder.build(platform, target, maxHops, PER_HOP_TIMEOUT_MILLIS)
        val process = processRunner.run(command, timeoutSeconds * MILLIS_PER_SECOND, MAX_OUTPUT_CHARACTERS)
        val hops = TracerouteOutputParser.parse(process.output, maxHops)
        val destinationReached = hops.any { it.address?.let { address -> sameAddress(address, target) } == true }
        val status =
            when {
                process.executableUnavailable -> "executable_unavailable"
                process.cancelled -> "cancelled"
                process.timedOut -> "deadline_exceeded"
                process.exitCode != 0 && hops.isEmpty() -> "process_failed"
                hops.isEmpty() -> "no_hop_data"
                else -> "completed"
            }
        return TracerouteResult(
            status,
            target.hostAddress,
            destinationReached,
            hops,
            elapsedMillis(startedAt),
            process.output.length >= MAX_OUTPUT_CHARACTERS,
        )
    }

    private fun selectTarget(
        addresses: List<InetAddress>,
        family: String,
    ): InetAddress? =
        addresses
            .filter {
                when (family) {
                    "ipv4" -> it is Inet4Address
                    "ipv6" -> it is Inet6Address
                    else -> true
                }
            }.distinctBy(InetAddress::getHostAddress)
            .sortedBy(InetAddress::getHostAddress)
            .firstOrNull()

    private fun sameAddress(
        value: String,
        target: InetAddress,
    ): Boolean =
        runCatching {
            val address = InetAddress.getByName(value.substringBefore('%'))
            address.address.contentEquals(target.address)
        }.getOrDefault(false)

    private fun result(
        status: String,
        target: String?,
        startedAt: Long,
    ) = TracerouteResult(status, target, false, emptyList(), elapsedMillis(startedAt), false)

    private fun elapsedMillis(startedAt: Long): Long = ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0)

    private companion object {
        const val PER_HOP_TIMEOUT_MILLIS = 1_000
        const val MAX_OUTPUT_CHARACTERS = 8_000
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
