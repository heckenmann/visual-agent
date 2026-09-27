package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerHealthDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerHealthDiagnosticsPort
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsHealthToolTest {
    @Test
    fun `returns component statuses without detailed configuration`() {
        val tool =
            DiagnosticsHealthTool(
                ServerHealthDiagnosticsPort {
                    Mono.just(
                        ServerHealthDiagnostic(
                            "degraded",
                            "healthy",
                            "unavailable",
                            "healthy",
                            providerReasons = listOf("provider_timeout"),
                        ),
                    )
                },
            )

        val result = tool.execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("\"status\":\"degraded\""))
        assertTrue(result.content.contains("\"provider\":\"unavailable\""))
        assertTrue(result.content.contains("provider_timeout"))
        assertFalse(result.content.contains("api-key"))
    }

    @Test
    fun `rejects arbitrary input`() {
        var calls = 0
        val tool =
            DiagnosticsHealthTool(
                ServerHealthDiagnosticsPort {
                    calls++
                    Mono.just(ServerHealthDiagnostic("healthy", "healthy", "healthy", "healthy"))
                },
            )

        val result = tool.execute("{\"path\":\"/private\"}", emptyMap())

        assertFalse(result.success)
        assertEquals("Input must be an empty JSON object.", result.error)
        assertEquals(0, calls)
    }
}
