package de.heckenmann.visualagent.knowledge

import java.nio.file.Path

/** Identifies a database startup failure without exposing SQL or stored values. */
enum class DatabaseMigrationFailureKind {
    /** The database contains schema changes from a newer application version. */
    NEWER_SCHEMA,

    /** A required pre-migration backup could not be completed. */
    BACKUP_FAILED,

    /** Flyway could not inspect, validate, or apply the schema migrations. */
    MIGRATION_FAILED,
}

/** Carries safe startup diagnostics for failed database upgrades. */
class DatabaseMigrationFailure(
    val kind: DatabaseMigrationFailureKind,
    val backupDirectory: Path? = null,
    cause: Throwable? = null,
) : IllegalStateException("Database startup migration failed: $kind", cause) {
    /** Returns an actionable message that does not include SQL or stored credentials. */
    fun userMessage(): String =
        when (kind) {
            DatabaseMigrationFailureKind.NEWER_SCHEMA ->
                "This database was opened by a newer Visual Agent version. Use that version or restore a compatible backup."
            DatabaseMigrationFailureKind.BACKUP_FAILED ->
                "A pre-upgrade backup could not be created. The database was not migrated; check disk space and server data permissions."
            DatabaseMigrationFailureKind.MIGRATION_FAILED ->
                if (backupDirectory == null) {
                    "The database upgrade failed. Keep the database files and check the server log before retrying."
                } else {
                    "The database upgrade failed. A pre-upgrade backup is at $backupDirectory. Keep both copies and check the server log."
                }
        }
}
