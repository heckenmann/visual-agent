package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnosticsPort
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsProviderToolTest {
    @Test
    fun `reports successful active provider probe without returning model identifiers`() {
        val tool =
            DiagnosticsProviderTool(
                ServerProviderDiagnosticsPort {
                    Mono.just(
                        ServerProviderDiagnostic(
                            "local",
                            "OLLAMA",
                            true,
                            false,
                            4,
                            true,
                            null,
                            endpointOrigin = "https://example.test",
                            selectedModelId = "selected-model",
                            selectedModelCapabilities = listOf("tools", "vision"),
                            selectedModelCapabilitiesComplete = true,
                        ),
                    )
                },
            )

        val result = tool.execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("\"healthy\":true"))
        assertTrue(result.content.contains("\"discoveredModelCount\":4"))
        assertTrue(result.content.contains("\"selectedModelCapabilities\":[\"tools\",\"vision\"]"))
        assertTrue(result.content.contains("\"selectedModelCapabilitiesComplete\":true"))
        assertFalse(result.content.contains("model-identifier"))
    }

    @Test
    fun `returns normalized provider failure without leaking its raw error`() {
        val tool =
            DiagnosticsProviderTool(
                ServerProviderDiagnosticsPort {
                    Mono.just(ServerProviderDiagnostic("cloud", "OPENAI_COMPATIBLE", true, true, null, false, "Authentication failed"))
                },
            )

        val output = tool.execute("{}", emptyMap()).content

        assertTrue(output.contains("Authentication failed"))
        assertFalse(output.contains("api-secret"))
        assertFalse(output.contains("Authorization"))
    }

    @Test
    fun `rejects arbitrary input`() {
        var calls = 0
        val tool =
            DiagnosticsProviderTool(
                ServerProviderDiagnosticsPort {
                    calls++
                    Mono.just(ServerProviderDiagnostic("local", "OLLAMA", true, false, 1, true, null))
                },
            )

        val result = tool.execute("{\"url\":\"https://user:secret@example.test\"}", emptyMap())

        assertFalse(result.success)
        assertEquals(0, calls)
    }
}
