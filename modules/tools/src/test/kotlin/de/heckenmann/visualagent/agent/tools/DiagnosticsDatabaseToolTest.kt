package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnosticsPort
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsDatabaseToolTest {
    @Test
    fun `returns bounded sanitized server database status`() {
        val tool =
            DiagnosticsDatabaseTool {
                Mono.just(ServerDatabaseDiagnostic(true, true, "3", 0, null))
            }

        val result = tool.executeReactive("{}", emptyMap()).block()!!

        assertTrue(result.success)
        assertTrue(result.content.contains("\"status\":\"healthy\""))
        assertTrue(result.content.contains("\"currentSchemaVersion\":\"3\""))
        assertFalse(result.content.contains("jdbc:"))
        assertFalse(result.content.contains("password"))
    }

    @Test
    fun `reports unavailable state without raw connection details`() {
        val tool =
            DiagnosticsDatabaseTool {
                Mono.just(ServerDatabaseDiagnostic(false, false, null, null, "database_connection_unavailable"))
            }

        val result = tool.executeReactive("{}", emptyMap()).block()!!

        assertTrue(result.content.contains("\"status\":\"unavailable\""))
        assertTrue(result.content.contains("database_connection_unavailable"))
        assertFalse(result.content.contains("sensitive driver detail"))
    }

    @Test
    fun `rejects input rather than permitting caller supplied queries`() {
        val tool = DiagnosticsDatabaseTool(ServerDatabaseDiagnosticsPort { error("must not query") })

        val result = tool.executeReactive("""{"query":"SELECT *"}""", emptyMap()).block()!!

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("empty JSON object"))
    }
}
