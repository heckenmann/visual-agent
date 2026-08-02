package de.heckenmann.visualagent.agent

import java.lang.reflect.Method

/**
 * Extracts provider response-body details from nested Spring AI exceptions.
 */
internal object ProviderErrorDetailExtractor {
    /**
     * Builds an [IllegalStateException] with the most specific provider error message available.
     *
     * @param fallbackMessage Message used when no exception detail is available
     * @param throwable Original provider error
     * @return Exception with response-body details when available
     */
    fun toIllegalState(
        fallbackMessage: String,
        throwable: Throwable,
    ): IllegalStateException = IllegalStateException(extractMessage(throwable, fallbackMessage), throwable)

    private fun extractMessage(
        throwable: Throwable,
        fallbackMessage: String,
    ): String {
        var current: Throwable? = throwable
        var fallback: String? = throwable.message?.takeIf { it.isNotBlank() }
        while (current != null) {
            val responseBody = invokeResponseBodyMethod(current)
            if (!responseBody.isNullOrBlank()) {
                val statusText = current.message?.takeIf { it.isNotBlank() }
                return listOfNotNull(statusText, responseBody.trim())
                    .joinToString(": ")
                    .trim()
            }
            if (!current.message.isNullOrBlank()) fallback = current.message
            current = current.cause
        }
        return fallback ?: fallbackMessage
    }

    private fun invokeResponseBodyMethod(throwable: Throwable): String? {
        val method: Method =
            runCatching { throwable.javaClass.getMethod("getResponseBodyAsString") }
                .getOrNull()
                ?: return null
        return runCatching { method.invoke(throwable) as? String }.getOrNull()
    }
}
