package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.EffectiveServerConfigurationDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ProviderConfigurationDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerConfigurationDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnosticsPort
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ServerHealthDiagnosticsPortAdapterTest {
    @Test
    fun `aggregates unavailable database and provider states without details`() {
        val adapter =
            ServerHealthDiagnosticsPortAdapter(
                ServerDatabaseDiagnosticsPort {
                    Mono.just(ServerDatabaseDiagnostic(false, false, null, null, "database_connection_unavailable"))
                },
                ServerProviderDiagnosticsPort {
                    Mono.just(ServerProviderDiagnostic("private-id", "OLLAMA", true, false, null, false, "Provider timeout"))
                },
                ServerConfigurationDiagnosticsPort { configuration() },
            )

        val status = adapter.snapshot().block()!!

        assertEquals("unavailable", status.status)
        assertEquals("unavailable", status.database)
        assertEquals("unavailable", status.provider)
        assertEquals("healthy", status.configuration)
        assertEquals(listOf("database_unreachable"), status.databaseReasons)
        assertEquals(listOf("provider_timeout"), status.providerReasons)
        assertEquals(emptyList(), status.configurationReasons)
    }

    @Test
    fun `reports degraded health when selected provider model is missing`() {
        val adapter =
            ServerHealthDiagnosticsPortAdapter(
                ServerDatabaseDiagnosticsPort {
                    Mono.just(ServerDatabaseDiagnostic(true, true, "12", 0, null))
                },
                ServerProviderDiagnosticsPort {
                    Mono.just(ServerProviderDiagnostic("provider", "OLLAMA", true, false, 1, false, null))
                },
                ServerConfigurationDiagnosticsPort { configuration() },
            )

        val status = adapter.snapshot().block()!!

        assertEquals("degraded", status.status)
        assertEquals("healthy", status.database)
        assertEquals("degraded", status.provider)
        assertEquals(listOf("selected_model_not_discovered"), status.providerReasons)
    }

    @Test
    fun `returns stable configuration reason codes without warning text`() {
        val warning = "unsafe private detail that must not be copied"
        val adapter =
            ServerHealthDiagnosticsPortAdapter(
                ServerDatabaseDiagnosticsPort { Mono.just(ServerDatabaseDiagnostic(true, true, "12", 0, null)) },
                ServerProviderDiagnosticsPort {
                    Mono.just(ServerProviderDiagnostic("provider", "OLLAMA", true, false, 1, true, null))
                },
                ServerConfigurationDiagnosticsPort {
                    configuration().copy(warnings = listOf(warning), warningCodes = listOf("context_window_non_positive"))
                },
            )

        val result = adapter.snapshot().block()!!

        assertEquals("degraded", result.status)
        assertEquals(listOf("context_window_non_positive"), result.configurationReasons)
        assertFalse(result.configurationReasons.any { warning in it })
    }

    @Test
    fun `maps unknown provider errors to a fixed reason code`() {
        val secretBearingError = "request failed at https://user:private-token@example.test/path"
        val adapter =
            ServerHealthDiagnosticsPortAdapter(
                ServerDatabaseDiagnosticsPort { Mono.just(ServerDatabaseDiagnostic(true, true, "12", 0, null)) },
                ServerProviderDiagnosticsPort {
                    Mono.just(ServerProviderDiagnostic("provider", "OLLAMA", true, false, null, false, secretBearingError))
                },
                ServerConfigurationDiagnosticsPort { configuration() },
            )

        val result = adapter.snapshot().block()!!

        assertEquals(listOf("provider_request_failed"), result.providerReasons)
        assertFalse(result.providerReasons.any { secretBearingError in it })
    }

    private fun configuration() =
        EffectiveServerConfigurationDiagnostic(
            provider = ProviderConfigurationDiagnostic("provider", "OLLAMA", true, null, false, "model", null, null, emptyList(), false),
            contextWindow = 32_000,
            timeoutSeconds = 60,
            maxParallelSubAgents = 4,
            warnings = emptyList(),
        )
}
