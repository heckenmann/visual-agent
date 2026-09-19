package de.heckenmann.visualagent.knowledge

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.assertEquals

/** Verifies onboarding state initialization and persistence in the H2 schema. */
class OnboardingStateMigrationTest {
    @Test
    fun `fresh database starts onboarding`() {
        val jdbcUrl = newDatabase("visual-agent-onboarding-fresh")
        migrate(jdbcUrl)

        assertEquals("NOT_STARTED", onboardingState(jdbcUrl))
    }

    @Test
    fun `existing onboarding state survives a repeated migration`() {
        val jdbcUrl = newDatabase("visual-agent-onboarding-existing")
        migrate(jdbcUrl)
        DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
            connection
                .prepareStatement(
                    "UPDATE user_preferences SET preference_value = ? WHERE preference_key = ?",
                ).use { statement ->
                    statement.setString(1, "COMPLETED")
                    statement.setString(2, "ui.onboarding.v1")
                    statement.executeUpdate()
                }
        }

        migrate(jdbcUrl)

        assertEquals("COMPLETED", onboardingState(jdbcUrl))
    }

    private fun newDatabase(prefix: String): String {
        val directory = Files.createTempDirectory(prefix)
        return "jdbc:h2:file:${directory.resolve("database")};DB_CLOSE_ON_EXIT=FALSE"
    }

    private fun migrate(jdbcUrl: String) {
        Flyway
            .configure()
            .dataSource(jdbcUrl, "sa", "")
            .locations("classpath:db/migration-h2")
            .load()
            .migrate()
    }

    private fun onboardingState(jdbcUrl: String): String =
        DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
            connection
                .prepareStatement(
                    "SELECT preference_value FROM user_preferences WHERE preference_key = ?",
                ).use { statement ->
                    statement.setString(1, "ui.onboarding.v1")
                    statement.executeQuery().use { result ->
                        check(result.next())
                        result.getString("preference_value")
                    }
                }
        }
}
