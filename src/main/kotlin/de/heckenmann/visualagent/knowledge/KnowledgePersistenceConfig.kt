package de.heckenmann.visualagent.knowledge

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.heckenmann.visualagent.config.AppConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.sqlite.SQLiteConfig
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
    fun dataSource(environment: Environment): DataSource {
        val sqliteConfig =
            SQLiteConfig().apply {
                setJournalMode(SQLiteConfig.JournalMode.WAL)
                setBusyTimeout(5_000)
                enforceForeignKeys(true)
            }
        val databasePath = environment.getProperty("visual-agent.db.path") ?: AppConfig.instance.databasePath
        val jdbcUrl = if (databasePath.startsWith("jdbc:sqlite:")) databasePath else "jdbc:sqlite:$databasePath"
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
}
