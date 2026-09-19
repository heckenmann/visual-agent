package de.heckenmann.visualagent.knowledge

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.heckenmann.visualagent.config.ServerDataPathResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource

/**
 * Configures the transitional H2 data source used while JPA stores are migrated to R2DBC.
 */
@Configuration
internal class KnowledgePersistenceConfig {
    /**
     * Creates the transitional H2 data source used by the synchronous JPA stores.
     *
     * @return Shared application data source
     */
    @Bean
    fun databasePath(environment: Environment): String = ServerDataPathResolver.databasePath(environment)

    /** Exposes the stable server data root to storage-adjacent services. */
    @Bean
    fun serverDataRoot(environment: Environment): Path = ServerDataPathResolver.serverDataRoot(environment)

    @Bean
    fun dataSource(databasePath: String): DataSource {
        val jdbcUrl = h2JdbcUrl(databasePath)
        createParentDirectory(databasePath)
        return HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 4
                minimumIdle = 1
                connectionTimeout = 5_000
            },
        )
    }

    private fun createParentDirectory(databasePath: String) {
        val path = databasePath.removePrefix("jdbc:h2:file:").substringBefore(';')
        if (path.isBlank() || path == ":memory:" || path.startsWith("file:")) {
            return
        }
        val parent = Path.of(path).parent ?: return
        runCatching { Files.createDirectories(parent) }.getOrElse { cause ->
            throw IllegalStateException(
                "Unable to create the Visual Agent server data directory '$parent'. " +
                    "Check its permissions or set visual-agent.db.path to a writable location.",
                cause,
            )
        }
    }

    private fun h2JdbcUrl(databasePath: String): String {
        if (databasePath.startsWith("jdbc:h2:mem:")) {
            return "jdbc:h2:mem:visual-agent-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        }
        if (databasePath.startsWith("jdbc:h2:")) return databasePath
        val path = databasePath
        return "jdbc:h2:file:${Path.of(path).toAbsolutePath().normalize()};DB_CLOSE_ON_EXIT=FALSE"
    }
}
