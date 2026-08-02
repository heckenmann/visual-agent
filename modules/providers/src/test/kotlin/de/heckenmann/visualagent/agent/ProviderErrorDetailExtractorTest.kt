package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ProviderErrorDetailExtractorTest {
    @Test
    fun `toIllegalState uses response body from nested cause`() {
        val root = RuntimeException("network error")
        val responseBodyException = ExceptionWithResponseBody("HTTP 401 Unauthorized", "{\"error\":\"invalid key\"}")
        root.initCause(responseBodyException)

        val error = ProviderErrorDetailExtractor.toIllegalState("fallback", root)

        assertEquals("HTTP 401 Unauthorized: {\"error\":\"invalid key\"}", error.message)
        assertSame(root, error.cause)
    }

    @Test
    fun `toIllegalState falls back to throwable message when no response body`() {
        val root = IllegalStateException("provider offline")

        val error = ProviderErrorDetailExtractor.toIllegalState("fallback", root)

        assertEquals("provider offline", error.message)
    }

    @Test
    fun `toIllegalState uses fallback when no detail available`() {
        val error = ProviderErrorDetailExtractor.toIllegalState("unknown provider error", Throwable(null as String?))

        assertEquals("unknown provider error", error.message)
    }

    @Test
    fun `toIllegalState uses latest cause message when root message is blank`() {
        val root = Exception("")
        val cause = IllegalStateException("late error")
        root.initCause(cause)

        val error = ProviderErrorDetailExtractor.toIllegalState("fallback", root)

        assertEquals("late error", error.message)
    }

    private class ExceptionWithResponseBody(
        message: String,
        private val responseBody: String,
    ) : Exception(message) {
        @Suppress("unused")
        fun getResponseBodyAsString(): String = responseBody
    }
}
