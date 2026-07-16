package de.heckenmann.visualagent.knowledge

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.sqlite.SQLiteConfig
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.sql.DataSource

/**
 * Configures the single-connection SQLite data source used by JPA and Flyway.
 */
@Configuration
internal class KnowledgePersistenceConfig {
    /**
     * Creates the application data source with SQLite WAL and lock timeout settings.
     *
     * @return Shared application data source
     */
    @Bean
    fun databasePath(environment: Environment): String = environment.getProperty("visual-agent.db.path") ?: bootstrapDatabasePath()

    @Bean
    fun dataSource(databasePath: String): DataSource {
        val sqliteConfig =
            SQLiteConfig().apply {
                setJournalMode(SQLiteConfig.JournalMode.WAL)
                setBusyTimeout(5_000)
                enforceForeignKeys(true)
            }
        val jdbcUrl = if (databasePath.startsWith("jdbc:sqlite:")) databasePath else "jdbc:sqlite:$databasePath"
        createParentDirectory(databasePath)
        return HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
                minimumIdle = 1
                connectionTimeout = 5_000
                dataSourceProperties = sqliteConfig.toProperties()
            },
        )
    }

    private fun bootstrapDatabasePath(): String {
        val configFile = File("src/main/resources/config/app.properties")
        if (!configFile.exists()) return "./data/visual-agent.db"
        val props = Properties()
        FileInputStream(configFile).use { props.load(it) }
        return props.getProperty("database.path", "./data/visual-agent.db")
    }

    private fun createParentDirectory(databasePath: String) {
        val path =
            if (databasePath.startsWith("jdbc:sqlite:")) {
                databasePath.removePrefix("jdbc:sqlite:")
            } else {
                databasePath
            }
        if (path.isBlank() || path == ":memory:" || path.startsWith("file:")) {
            return
        }
        Path.of(path).parent?.let(Files::createDirectories)
    }
}
