package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URI

/** Checks one HTTP(S) endpoint without exposing its body or request headers. */
@AgentTool
class NetworkHttpTool(
    private val probe: HttpDiagnosticProbe,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect one HTTP(S) endpoint from the Visual Agent server. Supports GET or HEAD, follows at most five " +
                    "safe redirects, and never returns response bodies or headers containing credentials. " +
                    "Input: {\"url\":\"https://example.org/health\",\"method\":\"GET\",\"timeoutSeconds\":10}.",
            inputSchema =
                """{"type":"object","properties":{"url":{"type":"string","maxLength":2048},"method":{"type":"string","enum":["GET","HEAD"]},"timeoutSeconds":{"type":"integer","minimum":1,"maximum":30}},"required":["url"],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val uri =
            runCatching { parseHttpDiagnosticUri(input.requiredString("url")) }
                .getOrElse { return failure(TOOL_ID.value, "Invalid URL. Supply an http or https URL without credentials or a fragment.") }
        val method = input.string("method")?.uppercase() ?: DEFAULT_METHOD
        if (method !in HTTP_METHODS) return failure(TOOL_ID.value, "Invalid method. Use GET or HEAD.")
        val timeoutSeconds = input.int("timeoutSeconds") ?: DEFAULT_TIMEOUT_SECONDS
        if (timeoutSeconds !in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) {
            return failure(TOOL_ID.value, "Invalid timeoutSeconds. Use a value from 1 to $MAX_TIMEOUT_SECONDS.")
        }

        val result = probe.inspect(uri, method, timeoutSeconds)
        val data =
            buildJsonObject {
                put("status", result.status)
                put("method", method)
                put("durationMillis", result.durationMillis)
                result.httpStatus?.let { put("httpStatus", it) }
                result.contentType?.let { put("contentType", it) }
                result.contentLength?.let { put("contentLength", it) }
                put("responseBodyExposed", false)
                putJsonArray("redirects") {
                    result.redirects.forEach { redirect ->
                        add(
                            buildJsonObject {
                                put("status", redirect.status)
                                put("from", redirect.from)
                                put("to", redirect.to)
                            },
                        )
                    }
                }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("network:http")
        val HTTP_METHODS = setOf("GET", "HEAD")
        const val DEFAULT_METHOD = "GET"
        const val DEFAULT_TIMEOUT_SECONDS = 10
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 30
    }
}

/** Normalized result from a bounded HTTP request. */
data class HttpDiagnosticResult(
    /** Stable result state. */
    val status: String,
    /** HTTP response status from the last response received. */
    val httpStatus: Int?,
    /** Safe content type, excluding all response headers. */
    val contentType: String?,
    /** Parsed content length, if valid. */
    val contentLength: Long?,
    /** Sanitized redirects, containing only scheme/host/port. */
    val redirects: List<HttpDiagnosticRedirect>,
    /** Total request duration in milliseconds. */
    val durationMillis: Long,
)

/** Sanitized redirect hop from one HTTP response. */
data class HttpDiagnosticRedirect(
    /** HTTP redirect response status. */
    val status: Int,
    /** Sanitized source authority. */
    val from: String,
    /** Sanitized destination authority. */
    val to: String,
)

/** Performs bounded HTTP endpoint diagnostics. */
fun interface HttpDiagnosticProbe {
    /** Inspect one validated URI using a bounded GET or HEAD request. */
    fun inspect(
        uri: URI,
        method: String,
        timeoutSeconds: Int,
    ): HttpDiagnosticResult
}

/** Minimal HTTP response metadata needed by the diagnostic probe. */
data class HttpDiagnosticResponse(
    /** Numeric HTTP status. */
    val status: Int,
    /** Content type, if present. */
    val contentType: String?,
    /** Parsed content length, if present and valid. */
    val contentLength: Long?,
    /** Redirect location, if present. */
    val location: String?,
)

/** Sends one HTTP request without following redirects and returns only bounded metadata. */
fun interface HttpDiagnosticTransport {
    /** Perform one GET/HEAD request, returning response metadata without a body. */
    fun request(
        uri: URI,
        method: String,
        timeoutMillis: Long,
    ): HttpDiagnosticResponse
}

/** Validate HTTP diagnostic URLs and reject embedded credentials or fragments. */
internal fun parseHttpDiagnosticUri(value: String): URI {
    require(value.length <= MAX_URL_LENGTH && value.none(Char::isWhitespace))
    val uri = URI(value)
    require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
    require(uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null)
    require(uri.port == -1 || uri.port in 1..MAX_PORT)
    normalizeNetworkHost(uri.host.removeSurrounding("[", "]"))
    return uri
}

private const val MAX_URL_LENGTH = 2_048
private const val MAX_PORT = 65_535
