package de.heckenmann.visualagent.error

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ErrorMessageMapperTest {
    @Test
    fun `404 model not found is translated into actionable message`() {
        val error = IllegalStateException("HTTP 404 Not Found from POST http://localhost:11434/api/chat")

        val userError = ErrorMessageMapper.map(error)

        assertEquals("Model not available", userError.summary)
        assertContains(userError.detail, "Pull the model")
        assertContains(userError.detail, "Session settings")
        assertFalse(userError.retryable)
    }

    @Test
    fun `model does not exist is recognized as missing model`() {
        val error = IllegalStateException("model does not exist or is not currently loaded")

        val userError = ErrorMessageMapper.map(error)

        assertEquals("Model not available", userError.summary)
        assertContains(userError.detail, "choose another model")
    }

    @Test
    fun `403 subscription error is translated without exposing raw response`() {
        val error =
            IllegalStateException(
                "HTTP 403 - {\"error\":\"this model requires a subscription, upgrade for access\"}",
            )

        val userError = ErrorMessageMapper.map(error)

        assertContains(userError.summary, "not available")
        assertFalse(userError.detail.contains("{\"error\""))
    }

    @Test
    fun `401 unauthorized is recognized`() {
        val userError = ErrorMessageMapper.map(IllegalStateException("401 unauthorized"))

        assertEquals("Authentication failed", userError.summary)
        assertContains(userError.detail, "API key")
    }

    @Test
    fun `429 quota error provides billing action`() {
        val userError = ErrorMessageMapper.map(IllegalStateException("429 current quota exceeded"))

        assertContains(userError.summary, "quota")
        assertContains(userError.detail, "billing")
    }

    @Test
    fun `timeout and connection errors are retryable`() {
        val timeoutError = ErrorMessageMapper.map(IllegalStateException("request timed out"))
        val connectionError = ErrorMessageMapper.map(IllegalStateException("connection refused"))

        assertEquals("Provider timeout", timeoutError.summary)
        assertEquals(true, timeoutError.retryable)
        assertEquals("Provider unreachable", connectionError.summary)
        assertEquals(true, connectionError.retryable)
    }

    @Test
    fun `nested causes contribute to provider classification`() {
        val error = IllegalStateException("request failed", IllegalArgumentException("invalid api key"))

        val userError = ErrorMessageMapper.map(error)

        assertEquals("Authentication failed", userError.summary)
    }

    @Test
    fun `unknown errors fall back to generic actionable message`() {
        val userError = ErrorMessageMapper.map(IllegalStateException("unexpected failure"))

        assertEquals("Request failed", userError.summary)
        assertContains(userError.detail, "Check the active provider and model")
        assertEquals(true, userError.retryable)
    }

    @Test
    fun `response body from nested cause is inspected without exposing it in user message`() {
        val root = RuntimeException("network error")
        val responseBodyException = ExceptionWithResponseBody("HTTP 404 Not Found", "{\"error\":\"model 'unknown' not found\"}")
        root.initCause(responseBodyException)

        val userError = ErrorMessageMapper.map(root)

        assertEquals("Model not available", userError.summary)
        assertFalse(userError.detail.contains("unknown"))
        assertFalse(userError.detail.contains("{\"error\""))
    }

    private class ExceptionWithResponseBody(
        message: String,
        private val responseBody: String,
    ) : Exception(message) {
        @Suppress("unused")
        fun getResponseBodyAsString(): String = responseBody
    }
}
