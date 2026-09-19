package de.heckenmann.visualagent.knowledge

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.assertTrue

/** Verifies the directory grant columns exposed by the H2 schema. */
class DirectoryGrantMigrationTest {
    @Test
    fun `fresh database migration creates the mapped directory grant owner column`() {
        val directory = Files.createTempDirectory("visual-agent-directory-grants")
        val jdbcUrl = "jdbc:h2:file:${directory.resolve("database")};DB_CLOSE_ON_EXIT=FALSE"

        Flyway
            .configure()
            .dataSource(jdbcUrl, "sa", "")
            .locations("classpath:db/migration-h2")
            .load()
            .migrate()

        DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'DIRECTORY_GRANTS'",
                    ).use { columns ->
                        val names = generateSequence { if (columns.next()) columns.getString("COLUMN_NAME") else null }.toSet()

                        assertTrue("OWNER_CLIENT_ID" in names)
                        assertTrue("CLIENT_BINDING_ID" !in names)
                    }
            }
        }
    }
}
