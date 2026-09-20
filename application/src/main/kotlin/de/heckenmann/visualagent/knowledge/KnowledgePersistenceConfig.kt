package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.config.ServerDataPathResolver
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import org.h2.jdbcx.JdbcDataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

/** Configures the embedded H2 database through Spring Data R2DBC. */
@Configuration(proxyBeanMethods = false)
internal class KnowledgePersistenceConfig {
    /** Resolves the compatibility database-location setting used by the server data-root service. */
    @Bean
    fun databasePath(environment: Environment): String = ServerDataPathResolver.databasePath(environment)

    /** Exposes the stable server data root to storage-adjacent services. */
    @Bean
    fun serverDataRoot(environment: Environment): Path = ServerDataPathResolver.serverDataRoot(environment)

    /** Resolves credentials for both legacy and newly created H2 file databases. */
    @Bean
    fun h2DatabaseCredentials(
        databasePath: String,
        serverDataRoot: Path,
    ): H2DatabaseCredentials = resolveCredentials(databasePath, serverDataRoot)

    /** Creates the JDBC data source used only by Spring Boot's Flyway initializer. */
    @Bean
    @Primary
    fun flywayDataSource(
        databasePath: String,
        serverDataRoot: Path,
        credentials: H2DatabaseCredentials,
    ): DataSource =
        JdbcDataSource().apply {
            setURL(flywayJdbcUrl(databasePath, serverDataRoot))
            user = credentials.user
            password = credentials.password
        }

    /** Creates the embedded file- or memory-backed H2 R2DBC connection factory. */
    @Bean
    fun connectionFactory(
        databasePath: String,
        serverDataRoot: Path,
        credentials: H2DatabaseCredentials,
    ): ConnectionFactory {
        Files.createDirectories(serverDataRoot)
        return ConnectionFactories.get(connectionOptions(databasePath, serverDataRoot, credentials))
    }

    /** Creates the database client used by all reactive store adapters. */
    @Bean
    fun databaseClient(connectionFactory: ConnectionFactory): DatabaseClient = DatabaseClient.create(connectionFactory)

    /** Creates the transaction manager for reactive H2 transactions. */
    @Bean
    fun reactiveTransactionManager(connectionFactory: ConnectionFactory): ReactiveTransactionManager =
        R2dbcTransactionManager(connectionFactory)

    /** Creates the operator used to scope multi-statement store operations atomically. */
    @Bean
    fun transactionalOperator(transactionManager: ReactiveTransactionManager): TransactionalOperator =
        TransactionalOperator.create(transactionManager)

    private fun connectionOptions(
        databasePath: String,
        serverDataRoot: Path,
        credentials: H2DatabaseCredentials,
    ): ConnectionFactoryOptions {
        if (databasePath.startsWith("jdbc:h2:mem:")) {
            val memoryName = databasePath.substringAfter("jdbc:h2:mem:").substringBefore(';').trim()
            return ConnectionFactoryOptions
                .builder()
                .option(ConnectionFactoryOptions.DRIVER, "h2")
                .option(ConnectionFactoryOptions.PROTOCOL, "mem")
                .option(ConnectionFactoryOptions.DATABASE, memoryName.ifBlank { "visual-agent" })
                .option(ConnectionFactoryOptions.USER, credentials.user)
                .option(ConnectionFactoryOptions.PASSWORD, credentials.password)
                .option(Option.valueOf("options"), "DB_CLOSE_DELAY=-1")
                .build()
        }
        val configuredPath =
            databasePath
                .removePrefix("jdbc:h2:file:")
                .substringBefore(';')
                .takeIf(String::isNotBlank)
                ?: serverDataRoot.resolve(DATABASE_FILE).toString()
        val databaseFile = Path.of(configuredPath).toAbsolutePath().normalize()
        Files.createDirectories(requireNotNull(databaseFile.parent))
        return ConnectionFactoryOptions
            .builder()
            .option(ConnectionFactoryOptions.DRIVER, "h2")
            .option(ConnectionFactoryOptions.PROTOCOL, "file")
            .option(ConnectionFactoryOptions.DATABASE, databaseFile.toString())
            .option(ConnectionFactoryOptions.USER, credentials.user)
            .option(ConnectionFactoryOptions.PASSWORD, credentials.password)
            .build()
    }

    private fun resolveCredentials(
        databasePath: String,
        serverDataRoot: Path,
    ): H2DatabaseCredentials {
        if (databasePath.startsWith("jdbc:h2:mem:")) return H2DatabaseCredentials("sa", "")
        val jdbcUrl = flywayJdbcUrl(databasePath, serverDataRoot)
        val candidates = listOf(H2DatabaseCredentials("", ""), H2DatabaseCredentials("sa", ""))
        return candidates.firstOrNull { credentials ->
            runCatching {
                JdbcDataSource()
                    .apply {
                        setURL(jdbcUrl)
                        user = credentials.user
                        password = credentials.password
                    }.connection
                    .use { }
            }.isSuccess
        } ?: error("Unable to authenticate to the H2 database with supported credentials")
    }

    private fun flywayJdbcUrl(
        databasePath: String,
        serverDataRoot: Path,
    ): String {
        if (databasePath.startsWith("jdbc:h2:mem:")) {
            val memoryName =
                databasePath
                    .substringAfter("jdbc:h2:mem:")
                    .substringBefore(';')
                    .trim()
                    .ifBlank { "visual-agent" }
            return "jdbc:h2:mem:$memoryName;DB_CLOSE_DELAY=-1"
        }
        val configuredPath =
            databasePath
                .removePrefix("jdbc:h2:file:")
                .substringBefore(';')
                .takeIf(String::isNotBlank)
                ?: serverDataRoot.resolve(DATABASE_FILE).toString()
        val databaseFile = Path.of(configuredPath).toAbsolutePath().normalize()
        Files.createDirectories(requireNotNull(databaseFile.parent))
        return "jdbc:h2:file:$databaseFile;DB_CLOSE_ON_EXIT=FALSE"
    }

    private companion object {
        const val DATABASE_FILE = "visual-agent"
    }
}

/** Credentials selected for one H2 database so Flyway and R2DBC use the same identity. */
internal data class H2DatabaseCredentials(
    val user: String,
    val password: String,
)
