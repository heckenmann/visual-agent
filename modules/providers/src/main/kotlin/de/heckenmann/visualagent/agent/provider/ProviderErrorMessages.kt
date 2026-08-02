package de.heckenmann.visualagent.agent.provider

/** Safe structured description of a provider failure. */
data class ProviderUserFacingError(
    /** Short failure summary. */
    val summary: String,
    /** Actionable detail without raw provider payloads. */
    val detail: String,
    /** Whether retrying the request can reasonably succeed. */
    val retryable: Boolean,
)

/** Converts provider exceptions into concise, actionable messages safe for the UI. */
object ProviderErrorMessages {
    /** Returns a user-facing string for the given provider error. */
    fun userFacing(error: Throwable): String {
        val mapped = userFacingError(error)
        return "${mapped.summary}: ${mapped.detail}"
    }

    /** Returns a structured, sanitized provider error. */
    fun userFacingError(error: Throwable): ProviderUserFacingError {
        val detail = exceptionChain(error)
        return when {
            "404" in detail || "not found" in detail || "model does not exist" in detail ->
                ProviderUserFacingError(
                    "Model not available",
                    "The configured model is not available on the provider. Pull the model or choose another model in Session settings.",
                    false,
                )
            "429" in detail || "quota" in detail || "rate limit" in detail ->
                ProviderUserFacingError(
                    "Provider quota exhausted",
                    "The provider rejected the request because no quota is currently available. Check billing or choose another provider.",
                    false,
                )
            "403" in detail || "subscription" in detail || "upgrade for access" in detail ->
                ProviderUserFacingError(
                    "Model not available for this account",
                    "The selected model is not available for this account. Choose another model or update the provider subscription.",
                    false,
                )
            "401" in detail || "unauthorized" in detail || "api key" in detail ->
                ProviderUserFacingError(
                    "Authentication failed",
                    "Authentication failed. Check the provider API key and base URL in Session settings.",
                    false,
                )
            "timeout" in detail || "timed out" in detail ->
                ProviderUserFacingError(
                    "Provider timeout",
                    "The provider did not respond before the request timeout. Try again or increase the timeout in Session settings.",
                    true,
                )
            "connection refused" in detail || "unknown host" in detail || "dns" in detail ->
                ProviderUserFacingError(
                    "Provider unreachable",
                    "The provider could not be reached. Check the connection and provider base URL.",
                    true,
                )
            else ->
                ProviderUserFacingError(
                    "Request failed",
                    "The model request failed. Check the active provider and model, then try again.",
                    true,
                )
        }
    }

    private fun exceptionChain(error: Throwable): String =
        generateSequence(error as Throwable?) { it.cause }
            .flatMap(::exceptionDetails)
            .joinToString(" ")
            .lowercase()

    private fun exceptionDetails(error: Throwable): List<String> =
        listOfNotNull(
            error.message?.takeIf { it.isNotBlank() },
            extractHttpStatus(error)?.toString(),
            extractResponseBody(error)?.takeIf { it.isNotBlank() },
        )

    private fun extractHttpStatus(error: Throwable): Int? =
        runCatching {
            val statusCode =
                error.javaClass.methods
                    .find { it.name == "getStatusCode" || it.name == "statusCode" }
                    ?.invoke(error)
            extractStatusValue(statusCode)
        }.getOrNull()

    private fun extractStatusValue(statusCode: Any?): Int? {
        if (statusCode is Number) return statusCode.toInt()
        return runCatching {
            statusCode
                ?.javaClass
                ?.methods
                ?.find { it.name == "value" || it.name == "getStatusCode" }
                ?.invoke(statusCode) as? Number
        }.getOrNull()?.toInt()
    }

    private fun extractResponseBody(error: Throwable): String? =
        runCatching {
            error.javaClass.getMethod("getResponseBodyAsString").invoke(error) as? String
        }.getOrNull()
}
