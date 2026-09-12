package de.heckenmann.visualagent.knowledge

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.assertTrue

class DirectoryGrantMigrationTest {
    @Test
    fun `fresh database migration creates the mapped directory grant owner column`() {
        val database = Files.createTempFile("visual-agent-directory-grants", ".db")
        val jdbcUrl = "jdbc:sqlite:$database"

        Flyway
            .configure()
            .dataSource(jdbcUrl, "", "")
            .locations("classpath:db/migration")
            .load()
            .migrate()

        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(directory_grants)").use { columns ->
                    val names = generateSequence { if (columns.next()) columns.getString("name") else null }.toSet()

                    assertTrue("owner_client_id" in names)
                    assertTrue("client_binding_id" !in names)
                }
            }
        }
    }
}
