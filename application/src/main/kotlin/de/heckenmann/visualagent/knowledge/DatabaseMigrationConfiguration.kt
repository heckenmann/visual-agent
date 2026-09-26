package de.heckenmann.visualagent.knowledge

import org.flywaydb.core.api.MigrationState
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Validates the database and takes a recoverable snapshot before schema changes. */
@Configuration(proxyBeanMethods = false)
internal class DatabaseMigrationConfiguration {
    /** Replaces Spring Boot's direct migrate call with a guarded migration. */
    @Bean
    fun flywayMigrationStrategy(backup: DatabaseMigrationBackup): FlywayMigrationStrategy =
        FlywayMigrationStrategy { flyway ->
            val migrationInfo =
                try {
                    flyway.info()
                } catch (failure: Exception) {
                    throw DatabaseMigrationFailure(DatabaseMigrationFailureKind.MIGRATION_FAILED, cause = failure)
                }
            if (migrationInfo.all().any { it.state == MigrationState.FUTURE_SUCCESS || it.state == MigrationState.FUTURE_FAILED }) {
                throw DatabaseMigrationFailure(DatabaseMigrationFailureKind.NEWER_SCHEMA)
            }

            val pending = migrationInfo.pending()
            val snapshot =
                if (pending.isEmpty()) {
                    null
                } else {
                    try {
                        backup.createIfNeeded(
                            sourceVersion = migrationInfo.current()?.version?.version,
                            targetVersion = pending.last().version.version,
                        )
                    } catch (failure: Exception) {
                        throw DatabaseMigrationFailure(DatabaseMigrationFailureKind.BACKUP_FAILED, cause = failure)
                    }
                }
            try {
                flyway.migrate()
            } catch (failure: Exception) {
                throw DatabaseMigrationFailure(DatabaseMigrationFailureKind.MIGRATION_FAILED, snapshot, failure)
            }
        }
}
