package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerLogQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies bounded retention, filtering, and secret redaction before log persistence in memory. */
class ServerLogBufferTest {
    @Test
    fun `retains only the newest entries and applies all filters`() {
        val buffer = ServerLogBuffer(2)
        buffer.append(1, "INFO", "app.First", "main", "startup", mapOf("correlationId" to "c1"))
        buffer.append(2, "ERROR", "app.Provider", "worker", "request failed", mapOf("correlationId" to "c2"))
        buffer.append(3, "ERROR", "app.Provider", "worker", "connection failed", mapOf("correlationId" to "c3"))

        val recentErrors = buffer.search(ServerLogQuery(level = "error", loggerContains = "provider", limit = 10))
        val correlated = buffer.search(ServerLogQuery(query = "connection", correlationId = "c3"))

        assertEquals(listOf("connection failed", "request failed"), recentErrors.map { it.message })
        assertEquals("c3", correlated.single().correlationId)
        assertFalse(buffer.search(ServerLogQuery(query = "startup")).any())
    }

    @Test
    fun `redacts credentials and truncates message before retaining`() {
        val buffer = ServerLogBuffer(4)
        buffer.append(
            1,
            "WARN",
            "logger",
            "worker",
            "Authorization: Bearer abc.def token apiKey=secret-value url=https://user:pass@example.test/path",
            emptyMap(),
        )
        buffer.append(2, "ERROR", "logger", "worker", "x".repeat(5_000), emptyMap())

        val redacted = buffer.search(ServerLogQuery()).last().message
        val truncated = buffer.search(ServerLogQuery()).first().message

        assertFalse(redacted.contains("abc.def"))
        assertFalse(redacted.contains("secret-value"))
        assertFalse(redacted.contains("user:pass"))
        assertTrue(redacted.contains("[REDACTED]"))
        assertEquals(2_048, truncated.length)
    }

    @Test
    fun `redacts a private key even when its closing marker is beyond the input bound`() {
        val buffer = ServerLogBuffer(1)
        buffer.append(
            1,
            "ERROR",
            "logger",
            "worker",
            "-----BEGIN PRIVATE KEY-----\n" + "sensitive-key-material".repeat(300) + "\n-----END PRIVATE KEY-----",
            emptyMap(),
        )

        val message = buffer.search(ServerLogQuery()).single().message

        assertEquals("[REDACTED PRIVATE KEY]", message)
        assertFalse(message.contains("sensitive-key-material"))
    }

    @Test
    fun `redacts generic key query parameters before retaining logs`() {
        val buffer = ServerLogBuffer(1)
        buffer.append(1, "WARN", "logger", "worker", "GET https://example.test/api?key=secret-value&mode=read", emptyMap())

        val message = buffer.search(ServerLogQuery()).single().message

        assertFalse(message.contains("secret-value"))
        assertTrue(message.contains("key=[REDACTED]"))
    }
}
