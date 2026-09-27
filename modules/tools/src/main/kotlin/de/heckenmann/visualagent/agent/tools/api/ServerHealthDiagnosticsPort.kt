package de.heckenmann.visualagent.agent.tools.api

import reactor.core.publisher.Mono

/** Bounded health summary for core Visual Agent server subsystems. */
data class ServerHealthDiagnostic(
    /** Overall status: healthy, degraded, or unavailable. */
    val status: String,
    /** Database subsystem status. */
    val database: String,
    /** Active provider subsystem status. */
    val provider: String,
    /** Effective configuration status. */
    val configuration: String,
    /** Stable, non-sensitive reasons for database status. */
    val databaseReasons: List<String> = emptyList(),
    /** Stable, non-sensitive reasons for provider status. */
    val providerReasons: List<String> = emptyList(),
    /** Stable, non-sensitive reasons for configuration status. */
    val configurationReasons: List<String> = emptyList(),
)

/** Aggregates safe health signals from the core server subsystems. */
fun interface ServerHealthDiagnosticsPort {
    /** Probes database, provider connectivity, and effective configuration. */
    fun snapshot(): Mono<ServerHealthDiagnostic>
}
