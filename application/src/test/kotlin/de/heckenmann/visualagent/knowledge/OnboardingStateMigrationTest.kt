package de.heckenmann.visualagent.knowledge

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.assertEquals

/** Verifies that onboarding migration distinguishes new and already-used Visual Agent databases. */
class OnboardingStateMigrationTest {
    @Test
    fun `fresh database starts onboarding`() {
        val database = Files.createTempFile("visual-agent-onboarding-fresh", ".db")
        migrate("jdbc:sqlite:$database")

        assertEquals("NOT_STARTED", onboardingState("jdbc:sqlite:$database"))
    }

    @Test
    fun `existing conversation migration does not block workspace startup`() {
        val database = Files.createTempFile("visual-agent-onboarding-existing", ".db")
        val jdbcUrl = "jdbc:sqlite:$database"
        Flyway
            .configure()
            .dataSource(jdbcUrl, "", "")
            .locations("classpath:db/migration")
            .target("14")
            .load()
            .migrate()
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection
                .prepareStatement("INSERT INTO conversation_history(id, session_id, role, content) VALUES (?, ?, ?, ?)")
                .use { statement ->
                    statement.setString(1, "migration-history")
                    statement.setString(2, "main")
                    statement.setString(3, "USER")
                    statement.setString(4, "Existing user data")
                    statement.executeUpdate()
                }
        }

        migrate(jdbcUrl)

        assertEquals("COMPLETED", onboardingState(jdbcUrl))
    }

    private fun migrate(jdbcUrl: String) {
        Flyway
            .configure()
            .dataSource(jdbcUrl, "", "")
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    private fun onboardingState(jdbcUrl: String): String =
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection
                .prepareStatement("SELECT value FROM user_preferences WHERE key = ?")
                .use { statement ->
                    statement.setString(1, "ui.onboarding.v1")
                    statement.executeQuery().use { result ->
                        check(result.next())
                        result.getString("value")
                    }
                }
        }
}
