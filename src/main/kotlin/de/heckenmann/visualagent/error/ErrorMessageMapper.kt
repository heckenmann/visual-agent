package de.heckenmann.visualagent.error

/**
 * Maps provider and infrastructure exceptions into concise, actionable messages safe for the UI.
 *
 * The mapper inspects the full exception chain (message and optional response body) and returns a
 * [UserFacingError] without exposing raw SDK stack traces, API keys, or response internals.
 */
internal object ErrorMessageMapper {
    /**
     * Convert a throwable into a [UserFacingError] the UI can render directly.
     *
     * @param throwable Original exception from a provider, tool, or infrastructure call
     * @return Structured, user-safe error description
     */
    fun map(throwable: Throwable): UserFacingError {
        val status = extractHttpStatus(throwable)
        val detail = buildDetailString(throwable)
        return when {
            status == 404 || isModelNotFound(detail) ->
                UserFacingError(
                    summary = "Model not available",
                    detail =
                        "The configured model is not available on the provider. " +
                            "Pull the model or choose another model in Session settings.",
                    retryable = false,
                )
            status == 429 || isQuota(detail) ->
                UserFacingError(
                    summary = "Provider quota exhausted",
                    detail =
                        "The provider rejected the request because no quota is currently available. " +
                            "Check billing or choose another provider.",
                    retryable = false,
                )
            status == 403 || isSubscription(detail) ->
                UserFacingError(
                    summary = "Model not available for this account",
                    detail =
                        "The selected model is not available for this account. " +
                            "Choose another model or update the provider subscription.",
                    retryable = false,
                )
            status == 401 || isAuthentication(detail) ->
                UserFacingError(
                    summary = "Authentication failed",
                    detail = "Authentication failed. Check the provider API key and base URL in Session settings.",
                    retryable = false,
                )
            isTimeout(detail) ->
                UserFacingError(
                    summary = "Provider timeout",
                    detail =
                        "The provider did not respond before the request timeout. " +
                            "Try again or increase the timeout in Session settings.",
                    retryable = true,
                )
            isConnection(detail) ->
                UserFacingError(
                    summary = "Provider unreachable",
                    detail = "The provider could not be reached. Check the connection and provider base URL.",
                    retryable = true,
                )
            else ->
                UserFacingError(
                    summary = "Request failed",
                    detail = "The model request failed. Check the active provider and model, then try again.",
                    retryable = true,
                )
        }
    }

    private fun isModelNotFound(detail: String): Boolean =
        "404" in detail || "not found" in detail || "model not found" in detail || "model does not exist" in detail

    private fun isQuota(detail: String): Boolean = "429" in detail || "quota" in detail || "rate limit" in detail

    private fun isSubscription(detail: String): Boolean = "403" in detail || "subscription" in detail || "upgrade for access" in detail

    private fun isAuthentication(detail: String): Boolean = "401" in detail || "unauthorized" in detail || "api key" in detail

    private fun isTimeout(detail: String): Boolean = "timeout" in detail || "timed out" in detail

    private fun isConnection(detail: String): Boolean = "connection refused" in detail || "unknown host" in detail || "dns" in detail

    private fun extractHttpStatus(throwable: Throwable): Int? {
        var current: Throwable? = throwable
        while (current != null) {
            val status = invokeStatusCodeMethod(current)
            if (status != null) return status
            current = current.cause
        }
        return null
    }

    private fun invokeStatusCodeMethod(throwable: Throwable): Int? =
        runCatching {
            val statusCode =
                throwable.javaClass.methods
                    .find { it.name == "getStatusCode" || it.name == "statusCode" }
                    ?.invoke(throwable)
            extractStatusValue(statusCode)
        }.getOrNull()

    private fun extractStatusValue(statusCode: Any?): Int? {
        if (statusCode == null) return null
        if (statusCode is Int) return statusCode
        if (statusCode is Number) return statusCode.toInt()
        val statusNumber =
            runCatching {
                statusCode.javaClass.methods
                    .find { it.name == "value" || it.name == "getStatusCode" }
                    ?.let { method -> method.invoke(statusCode) as? Number }
            }.getOrNull()
        return statusNumber?.toInt()
    }

    private fun buildDetailString(throwable: Throwable): String {
        val builder = StringBuilder()
        var current: Throwable? = throwable
        while (current != null) {
            current.message?.takeIf { it.isNotBlank() }?.let { builder.append(' ').append(it) }
            extractResponseBody(current)?.takeIf { it.isNotBlank() }?.let {
                builder.append(' ').append(it)
            }
            current = current.cause
        }
        return builder.toString().lowercase()
    }

    private fun extractResponseBody(throwable: Throwable): String? =
        runCatching {
            val method = throwable.javaClass.getMethod("getResponseBodyAsString")
            method.invoke(throwable) as? String
        }.getOrNull()
}
