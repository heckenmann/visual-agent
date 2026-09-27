package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerConnectorDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerConnectorDiagnosticsPort
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsConnectorsToolTest {
    @Test
    fun `reports unavailable connector framework without exposing secrets`() {
        val tool =
            DiagnosticsConnectorsTool {
                ServerConnectorDiagnostic(false, null, "connector_framework_not_configured")
            }

        val result = tool.execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("not_available"))
        assertTrue(result.content.contains("connector_framework_not_configured"))
        assertFalse(result.content.contains("password"))
    }

    @Test
    fun `rejects arguments instead of accepting connector mutations`() {
        var called = false
        val tool =
            DiagnosticsConnectorsTool(
                ServerConnectorDiagnosticsPort {
                    called = true
                    ServerConnectorDiagnostic(true, 1, null)
                },
            )

        val result = tool.execute("{\"action\":\"connect\"}", emptyMap())

        assertFalse(result.success)
        assertFalse(called)
    }
}
