package de.heckenmann.visualagent.agent.tools.api

import reactor.core.publisher.Mono

/** Sanitized server database and schema health information. */
data class ServerDatabaseDiagnostic(
    /** Whether a lightweight connection probe succeeded. */
    val reachable: Boolean,
    /** Whether Flyway schema metadata could be read. */
    val schemaHistoryAvailable: Boolean,
    /** Latest successfully applied migration version, if available. */
    val currentSchemaVersion: String?,
    /** Number of failed migration records, if schema history is available. */
    val failedMigrationCount: Int?,
    /** Normalized failure category without raw SQL or driver messages. */
    val failureKind: String?,
)

/** Provides bounded, read-only diagnostics for the application database. */
fun interface ServerDatabaseDiagnosticsPort {
    /** Queries database reachability and schema metadata without reading application data. */
    fun snapshot(): Mono<ServerDatabaseDiagnostic>
}
