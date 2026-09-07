package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolError
import de.heckenmann.visualagent.agent.tools.api.ToolErrorCode
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

/** Converts internal tool execution results into the provider-facing JSON-only contract. */
internal object ToolResultNormalization {
    private val sensitiveValue =
        Regex("(?i)(api[_-]?key|authorization|password|passwd|token|secret|private[_-]?key)\\s*[:=]\\s*[^\\s,]+")

    fun envelope(result: ToolResult): ToolResultEnvelope =
        if (result.success) {
            ToolResultEnvelope(toolId = result.toolId, success = true, data = data(result.content))
        } else {
            ToolResultEnvelope(
                toolId = result.toolId,
                success = false,
                data = JsonNull,
                error = legacyError(result.error),
            )
        }

    fun executionError(error: Throwable): ToolError =
        when (rootCause(error)) {
            is ToolInputException,
            is IllegalArgumentException,
            ->
                ToolError(
                    code = ToolErrorCode.INVALID_ARGUMENT,
                    message = "The tool arguments are invalid.",
                    remediation = "Check the tool schema and provide the required values.",
                    retryable = false,
                )
            is NoSuchFileException ->
                ToolError(
                    code = ToolErrorCode.NOT_FOUND,
                    message = "The requested workspace file was not found.",
                    remediation = "List workspace files and retry with an existing path.",
                    retryable = false,
                )
            is AccessDeniedException,
            is SecurityException,
            ->
                ToolError(
                    code = ToolErrorCode.PERMISSION_DENIED,
                    message = "The tool is not permitted to access the requested resource.",
                    remediation = "Use an allowed workspace resource or request the required access.",
                    retryable = false,
                )
            else ->
                ToolError(
                    code = ToolErrorCode.EXECUTION_FAILED,
                    message = "The tool could not complete the requested operation.",
                    remediation = "Check the arguments and current application state, then retry or choose another tool.",
                    retryable = true,
                )
        }

    fun legacyError(error: ToolError): String = "${error.code.name}: ${error.message}"

    private fun data(content: String): JsonElement =
        if (content.isBlank()) {
            JsonNull
        } else {
            runCatching { Json.parseToJsonElement(content) }.getOrElse { JsonPrimitive(content) }
        }

    private fun legacyError(error: String?): ToolError {
        val message = sanitize(error).ifBlank { "The tool could not complete the requested operation." }
        val code = errorCode(error)
        return ToolError(
            code = code,
            message = message.removePrefix("${code.name}: "),
            remediation = remediation(code),
            retryable = code in setOf(ToolErrorCode.UNAVAILABLE, ToolErrorCode.TIMEOUT, ToolErrorCode.EXECUTION_FAILED),
        )
    }

    private fun errorCode(error: String?): ToolErrorCode =
        when (error?.substringBefore(':')?.trim()?.uppercase()) {
            "TOOL_ARGUMENTS" -> ToolErrorCode.INVALID_ARGUMENT
            "TOOL_TIMEOUT" -> ToolErrorCode.TIMEOUT
            "TOOL_CANCELLED" -> ToolErrorCode.CANCELLED
            "PERMISSION_DENIED" -> ToolErrorCode.PERMISSION_DENIED
            "NOT_FOUND" -> ToolErrorCode.NOT_FOUND
            "UNAVAILABLE" -> ToolErrorCode.UNAVAILABLE
            else -> ToolErrorCode.EXECUTION_FAILED
        }

    private fun remediation(code: ToolErrorCode): String =
        when (code) {
            ToolErrorCode.INVALID_ARGUMENT -> "Check the tool schema and provide the required values."
            ToolErrorCode.NOT_FOUND -> "List available resources and retry with an existing identifier or path."
            ToolErrorCode.UNAVAILABLE -> "Check the required service or configuration, then retry."
            ToolErrorCode.TIMEOUT -> "Retry with a larger timeout when the operation is expected to take longer."
            ToolErrorCode.CANCELLED -> "The call was cancelled; retry only if the user still wants the operation."
            ToolErrorCode.PERMISSION_DENIED -> "Use an allowed resource or request the required access."
            ToolErrorCode.EXECUTION_FAILED -> "Check the arguments and application state, then retry or choose another tool."
            ToolErrorCode.MALFORMED_RESULT -> "Retry the operation; the tool returned an invalid internal result."
        }

    private fun sanitize(value: String?): String = sensitiveValue.replace(value.orEmpty()) { "[redacted]" }.take(500)

    private fun rootCause(error: Throwable): Throwable = generateSequence(error) { it.cause }.last()
}
