package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerLogDiagnosticEntry
import de.heckenmann.visualagent.agent.tools.api.ServerLogDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ServerLogQuery
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies bounded filters and safe output for model-facing log diagnostics. */
class DiagnosticsLogsToolTest {
    @Test
    fun `returns sanitized structured matches with bounded query fields`() {
        var captured: ServerLogQuery? = null
        val tool =
            DiagnosticsLogsTool(
                ServerLogDiagnosticsPort { query ->
                    captured = query
                    Mono.just(listOf(ServerLogDiagnosticEntry(4, 10, "ERROR", "sample.Logger", "worker", "connection failed", "corr-4")))
                },
            )

        val result =
            tool
                .executeReactive(
                    """{"level":"error","loggerContains":"sample","query":"failed","correlationId":"corr-4","limit":7}""",
                    emptyMap(),
                ).block()!!

        assertTrue(result.success)
        assertEquals(ServerLogQuery("ERROR", "sample", "failed", "corr-4", 7), captured)
        assertTrue(result.content.contains("connection failed"))
        assertTrue(result.content.contains("visual-agent-server"))
    }

    @Test
    fun `rejects invalid levels oversized input and unsupported filters without reading logs`() {
        var searches = 0
        val tool =
            DiagnosticsLogsTool(
                ServerLogDiagnosticsPort {
                    searches++
                    Mono.just(emptyList())
                },
            )

        val invalidLevel = tool.executeReactive("""{"level":"FATAL"}""", emptyMap()).block()!!
        val unsupportedField = tool.executeReactive("""{"path":"/private/log"}""", emptyMap()).block()!!
        val oversized = tool.executeReactive("{" + " ".repeat(4_096) + "}", emptyMap()).block()!!

        assertFalse(invalidLevel.success)
        assertFalse(unsupportedField.success)
        assertFalse(oversized.success)
        assertEquals(0, searches)
    }

    @Test
    fun `caps the requested result count`() {
        val tool = DiagnosticsLogsTool(ServerLogDiagnosticsPort { Mono.just(emptyList()) })

        val result = tool.executeReactive("""{"limit":101}""", emptyMap()).block()!!

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("between 1 and 100"))
    }
}
