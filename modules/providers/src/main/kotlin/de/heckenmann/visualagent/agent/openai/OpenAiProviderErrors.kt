package de.heckenmann.visualagent.agent.openai

import java.lang.reflect.Method

/** Preserves provider response details while isolating error parsing from the chat client. */
internal object OpenAiProviderErrors {
    fun detailed(throwable: Throwable?): Throwable {
        if (throwable == null) return IllegalStateException("Unknown OpenAI chat model error")
        val detailedMessage = extractDetailedErrorMessage(throwable)
        return IllegalStateException(detailedMessage, throwable)
    }

    private fun extractDetailedErrorMessage(throwable: Throwable): String {
        var current: Throwable? = throwable
        var fallbackMessage: String? = throwable.message?.takeIf { it.isNotBlank() }
        while (current != null) {
            val responseBody = invokeResponseBodyMethod(current)
            if (!responseBody.isNullOrBlank()) {
                val statusText = current.message?.takeIf { it.isNotBlank() }
                return listOfNotNull(statusText, responseBody.trim()).joinToString(": ").trim()
            }
            if (!current.message.isNullOrBlank()) fallbackMessage = current.message
            current = current.cause
        }
        return fallbackMessage ?: "Unknown OpenAI chat model error"
    }

    private fun invokeResponseBodyMethod(throwable: Throwable): String? {
        val method: Method =
            runCatching { throwable.javaClass.getMethod("getResponseBodyAsString") }
                .getOrNull()
                ?: return null
        return runCatching { method.invoke(throwable) as? String }.getOrNull()
    }
}
