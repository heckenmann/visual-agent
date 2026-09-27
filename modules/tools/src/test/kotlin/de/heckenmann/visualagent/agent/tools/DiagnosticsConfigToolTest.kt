package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.EffectiveServerConfigurationDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ProviderConfigurationDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerConfigurationDiagnosticsPort
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsConfigToolTest {
    @Test
    fun `returns sanitized effective values and validation warnings`() {
        val tool =
            DiagnosticsConfigTool(
                ServerConfigurationDiagnosticsPort {
                    EffectiveServerConfigurationDiagnostic(
                        provider =
                            ProviderConfigurationDiagnostic(
                                providerId = "custom-provider",
                                adapter = "OPENAI_COMPATIBLE",
                                enabled = true,
                                endpointOrigin = "https://llm.example:8443",
                                apiKeyConfigured = true,
                                modelId = "model-x",
                                modelContextLimit = 65_536,
                                modelOutputLimit = 4_096,
                                modelCapabilities = listOf("tools", "vision"),
                                modelCapabilitiesComplete = false,
                            ),
                        contextWindow = 32_000,
                        timeoutSeconds = 90,
                        maxParallelSubAgents = 4,
                        warnings = listOf("The active model is not present in the enabled model catalog."),
                    )
                },
            )

        val result = tool.execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("\"valid\":false"))
        assertTrue(result.content.contains("\"endpointOrigin\":\"https://llm.example:8443\""))
        assertTrue(result.content.contains("\"apiKeyConfigured\":true"))
        assertTrue(result.content.contains("\"modelCapabilities\":[\"tools\",\"vision\"]"))
        assertTrue(result.content.contains("model catalog"))
        assertFalse(result.content.contains("/v1/chat/completions"))
        assertFalse(result.content.contains("raw-secret"))
    }

    @Test
    fun `does not expose provider details when configuration source is unavailable`() {
        val tool = DiagnosticsConfigTool(ServerConfigurationDiagnosticsPort { error("raw-secret") })

        val result = tool.execute("{}", emptyMap())

        assertFalse(result.success)
        assertFalse(result.content.contains("raw-secret"))
        assertFalse(result.error.orEmpty().contains("raw-secret"))
    }
}
