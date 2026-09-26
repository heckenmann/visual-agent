package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Verifies that an unsafe upgrade fails closed without discarding user data. */
class DatabaseMigrationFailureTest {
    @Test
    fun `unwritable backup destination blocks migration before schema changes`() {
        val root = Files.createTempDirectory("visual-agent-backup-failure")
        val database = root.resolve("database")
        val source = v2Database(database)
        seedUserValue(source)
        Files.writeString(root.resolve("migration-backups"), "occupied")

        val failure = assertMigrationFailure(database, DatabaseMigrationFailureKind.BACKUP_FAILED)
        assertTrue(failure.userMessage().contains("not migrated"))
        assertEquals("2", currentVersion(source))
        assertEquals("retained", userValue(source))
    }

    @Test
    fun `validation failure retains pre-upgrade snapshot and hides sensitive stored values`() {
        val root = Files.createTempDirectory("visual-agent-validation-failure")
        val database = root.resolve("database")
        val source = v2Database(database)
        seedUserValue(source)
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("UPDATE \"flyway_schema_history\" SET \"checksum\" = -1 WHERE \"version\" = '2'")
            }
        }

        val failure = assertMigrationFailure(database, DatabaseMigrationFailureKind.MIGRATION_FAILED)
        val snapshot = assertNotNull(failure.backupDirectory)
        assertTrue(Files.isRegularFile(snapshot.resolve("database.zip")))
        assertFalse(failure.userMessage().contains("retained"))
        assertEquals("2", currentVersion(source))
        assertEquals("retained", userValue(source))
    }

    @Test
    fun `database from a future schema is rejected before backup or migration`() {
        val root = Files.createTempDirectory("visual-agent-future-schema")
        val database = root.resolve("database")
        val source = v2Database(database)
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO \"flyway_schema_history\" " +
                        "(\"installed_rank\", \"version\", \"description\", \"type\", \"script\", \"checksum\", " +
                        "\"installed_by\", \"execution_time\", \"success\") " +
                        "VALUES (100, '999', 'future', 'SQL', 'V999__future.sql', 1, 'test', 1, TRUE)",
                )
            }
        }

        val failure = assertMigrationFailure(database, DatabaseMigrationFailureKind.NEWER_SCHEMA)
        assertTrue(failure.userMessage().contains("newer Visual Agent version"))
        assertFalse(Files.exists(root.resolve("migration-backups")))
        assertEquals("999", currentVersion(source))
    }

    private fun assertMigrationFailure(
        database: Path,
        expectedKind: DatabaseMigrationFailureKind,
    ): DatabaseMigrationFailure {
        val thrown = runCatching { KnowledgeDbTestFactory.create(database.toString()).close() }.exceptionOrNull()
        assertNotNull(thrown)
        val failure = generateSequence(thrown) { it.cause }.filterIsInstance<DatabaseMigrationFailure>().firstOrNull()
        assertNotNull(failure)
        assertEquals(expectedKind, failure.kind)
        return failure
    }

    private fun v2Database(path: Path): JdbcDataSource =
        JdbcDataSource().apply {
            setURL("jdbc:h2:file:$path;DB_CLOSE_ON_EXIT=FALSE")
            user = "sa"
            password = ""
            Flyway
                .configure()
                .dataSource(this)
                .locations("classpath:db/migration-h2")
                .target(MigrationVersion.fromVersion("2"))
                .load()
                .migrate()
        }

    private fun seedUserValue(source: JdbcDataSource) {
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO user_preferences (preference_key, preference_value) VALUES ('test.secret','retained')",
                )
            }
        }
    }

    private fun currentVersion(source: JdbcDataSource): String? = scalar(source, "SELECT MAX(\"version\") FROM \"flyway_schema_history\"")

    private fun userValue(source: JdbcDataSource): String? =
        scalar(source, "SELECT preference_value FROM user_preferences WHERE preference_key='test.secret'")

    private fun scalar(
        source: JdbcDataSource,
        sql: String,
    ): String? =
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result -> if (result.next()) result.getString(1) else null }
            }
        }
}
