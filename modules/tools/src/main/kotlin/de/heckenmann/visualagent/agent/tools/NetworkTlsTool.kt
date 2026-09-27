package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Checks TLS certificate, trust, hostname, and negotiated protocol for one endpoint. */
@AgentTool
class NetworkTlsTool(
    private val probe: TlsDiagnosticProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect TLS for one server-side host and port using platform roots plus Visual Agent's managed CA roots " +
                    "and hostname verification. " +
                    "Returns public certificate metadata only; it never exposes key material. " +
                    "Input: {\"host\":\"example.org\",\"port\":443,\"family\":\"auto\",\"timeoutSeconds\":10}.",
            inputSchema =
                """{"type":"object","properties":{"host":{"type":"string"},"port":{"type":"integer","minimum":1,"maximum":65535},"family":{"type":"string","enum":["auto","ipv4","ipv6"]},"timeoutSeconds":{"type":"integer","minimum":1,"maximum":30}},"required":["host"],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val host =
            runCatching { normalizeNetworkHost(input.requiredString("host")) }
                .getOrElse { return failure(TOOL_ID.value, "Invalid host. Supply a hostname or IP address, not a URL or path.") }
        val port = input.int("port") ?: DEFAULT_PORT
        if (port !in MIN_PORT..MAX_PORT) return failure(TOOL_ID.value, "Invalid port. Use a value from 1 to $MAX_PORT.")
        val family = input.string("family")?.lowercase() ?: DEFAULT_FAMILY
        if (family !in ADDRESS_FAMILIES) return failure(TOOL_ID.value, "Invalid family. Use auto, ipv4, or ipv6.")
        val timeoutSeconds = input.int("timeoutSeconds") ?: DEFAULT_TIMEOUT_SECONDS
        if (timeoutSeconds !in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) {
            return failure(TOOL_ID.value, "Invalid timeoutSeconds. Use a value from 1 to $MAX_TIMEOUT_SECONDS.")
        }

        val result = probe.inspect(host, port, family, timeoutSeconds)
        val data =
            buildJsonObject {
                put("host", host)
                put("port", port)
                put("family", family)
                put("status", result.status)
                result.address?.let { put("address", it) }
                result.tlsProtocol?.let { put("tlsProtocol", it) }
                result.cipherSuite?.let { put("cipherSuite", it) }
                result.hostnameVerified?.let { put("hostnameVerified", it) }
                result.trustValidated?.let { put("trustValidated", it) }
                result.certificate?.let { certificate ->
                    put(
                        "certificate",
                        buildJsonObject {
                            put("subject", certificate.subject)
                            put("issuer", certificate.issuer)
                            put("notBefore", certificate.notBefore)
                            put("notAfter", certificate.notAfter)
                            put("validAtCheckTime", certificate.validAtCheckTime)
                            putJsonArray("dnsNames") { certificate.dnsNames.forEach { add(JsonPrimitive(it)) } }
                        },
                    )
                }
                put("elapsedMillis", result.elapsedMillis)
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("network:tls")
        val ADDRESS_FAMILIES = setOf("auto", "ipv4", "ipv6")
        const val DEFAULT_FAMILY = "auto"
        const val DEFAULT_PORT = 443
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
        const val DEFAULT_TIMEOUT_SECONDS = 10
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 30
    }
}

/** Safe public fields from the peer's end-entity X.509 certificate. */
data class TlsCertificateSummary(
    /** Bounded X.500 subject distinguished name. */
    val subject: String,
    /** Bounded X.500 issuer distinguished name. */
    val issuer: String,
    /** Certificate validity start in ISO-8601 UTC. */
    val notBefore: String,
    /** Certificate validity end in ISO-8601 UTC. */
    val notAfter: String,
    /** Whether the certificate is within its validity period at the server clock. */
    val validAtCheckTime: Boolean,
    /** Bounded DNS subject alternative names. */
    val dnsNames: List<String>,
)

/** Normalized outcome of one JVM TLS inspection. */
data class TlsDiagnosticResult(
    /** Stable result status. */
    val status: String,
    /** Selected server address. */
    val address: String?,
    /** Negotiated protocol when the TLS handshake completed. */
    val tlsProtocol: String?,
    /** Negotiated cipher-suite name when available. */
    val cipherSuite: String?,
    /** Whether JSSE HTTPS endpoint identification passed. */
    val hostnameVerified: Boolean?,
    /** Whether the server's platform and application-managed trust policy accepted the certificate chain. */
    val trustValidated: Boolean?,
    /** Public certificate summary, if the peer certificate was captured. */
    val certificate: TlsCertificateSummary?,
    /** Total elapsed time in milliseconds. */
    val elapsedMillis: Long,
)

/** Performs bounded TLS endpoint diagnostics. */
fun interface TlsDiagnosticProbe {
    /** Inspect [host] and [port] with the selected address family and total timeout. */
    fun inspect(
        host: String,
        port: Int,
        family: String,
        timeoutSeconds: Int,
    ): TlsDiagnosticResult
}
