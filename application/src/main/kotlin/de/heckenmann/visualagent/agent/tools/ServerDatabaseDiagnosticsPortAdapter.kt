package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnosticsPort
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Runs static, read-only probes against the application's R2DBC database. */
@Component
class ServerDatabaseDiagnosticsPortAdapter(
    private val databaseClient: DatabaseClient,
) : ServerDatabaseDiagnosticsPort {
    override fun snapshot(): Mono<ServerDatabaseDiagnostic> =
        databaseClient
            .sql("SELECT 1 AS probe")
            .map { row, _ -> row.get("probe", Int::class.javaObjectType) != null }
            .one()
            .flatMap { reachable ->
                if (!reachable) {
                    Mono.just(unavailable())
                } else {
                    readMigrationState()
                }
            }.onErrorReturn(unavailable())

    private fun readMigrationState(): Mono<ServerDatabaseDiagnostic> =
        databaseClient
            .sql(
                "SELECT MAX(\"version\") AS current_version, " +
                    "SUM(CASE WHEN \"success\" = FALSE THEN 1 ELSE 0 END) AS failed_count " +
                    "FROM \"flyway_schema_history\"",
            ).map { row, _ ->
                ServerDatabaseDiagnostic(
                    reachable = true,
                    schemaHistoryAvailable = true,
                    currentSchemaVersion = row.get("current_version", String::class.java),
                    failedMigrationCount = (row.get("failed_count") as? Number)?.toInt() ?: 0,
                    failureKind = null,
                )
            }.one()
            .onErrorReturn(
                ServerDatabaseDiagnostic(
                    reachable = true,
                    schemaHistoryAvailable = false,
                    currentSchemaVersion = null,
                    failedMigrationCount = null,
                    failureKind = "migration_metadata_unavailable",
                ),
            )

    private fun unavailable() =
        ServerDatabaseDiagnostic(
            reachable = false,
            schemaHistoryAvailable = false,
            currentSchemaVersion = null,
            failedMigrationCount = null,
            failureKind = "database_connection_unavailable",
        )
}
