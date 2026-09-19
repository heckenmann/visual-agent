package de.heckenmann.visualagent.knowledge

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL
import io.r2dbc.spi.ConnectionFactoryOptions.USER
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import java.nio.file.Files
import java.nio.file.Path

/**
 * Provides the explicit H2/R2DBC infrastructure used during the persistence migration.
 *
 * The configuration is opt-in until all existing JPA transactions have been migrated. Keeping
 * the connection factory explicit prevents Spring Boot from creating a second global transaction
 * manager while the legacy persistence stack is still active.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "visual-agent.persistence", name = ["engine"], havingValue = "h2-r2dbc")
internal class ReactiveKnowledgePersistenceConfig {
    /** Creates a file-backed H2 R2DBC connection factory below the server data root. */
    @Bean("reactiveConnectionFactory")
    fun connectionFactory(serverDataRoot: Path): ConnectionFactory {
        Files.createDirectories(serverDataRoot)
        val databaseFile = serverDataRoot.resolve(DATABASE_FILE).toAbsolutePath().normalize()
        return ConnectionFactories.get(
            io.r2dbc.spi.ConnectionFactoryOptions
                .builder()
                .option(DRIVER, "h2")
                .option(PROTOCOL, "file")
                .option(DATABASE, databaseFile.toString())
                .option(USER, "sa")
                .build(),
        )
    }

    /** Creates the Spring Data R2DBC template for explicit reactive store adapters. */
    @Bean
    fun reactiveEntityTemplate(connectionFactory: ConnectionFactory): R2dbcEntityTemplate = R2dbcEntityTemplate(connectionFactory)

    /** Creates a low-level client for migration DDL and database-neutral custom queries. */
    @Bean
    fun reactiveDatabaseClient(connectionFactory: ConnectionFactory): DatabaseClient = DatabaseClient.create(connectionFactory)

    /** Creates the explicitly named transaction manager for reactive persistence adapters. */
    @Bean("reactiveTransactionManager")
    fun reactiveTransactionManager(
        @Qualifier("reactiveConnectionFactory") connectionFactory: ConnectionFactory,
    ): ReactiveTransactionManager = R2dbcTransactionManager(connectionFactory)

    /** Creates the transaction operator used by reactive store adapters. */
    @Bean("reactiveTransactionalOperator")
    fun reactiveTransactionalOperator(
        @Qualifier("reactiveTransactionManager") transactionManager: ReactiveTransactionManager,
    ): TransactionalOperator = TransactionalOperator.create(transactionManager)

    /** Creates the reactive preference adapter without replacing the legacy synchronous store yet. */
    @Bean
    fun reactivePreferenceStore(
        @Qualifier("reactiveDatabaseClient") databaseClient: DatabaseClient,
        @Qualifier("reactiveTransactionalOperator") transactionOperator: TransactionalOperator,
    ): ReactivePreferenceStore = R2dbcPreferenceStore(databaseClient, transactionOperator)

    /** Initializes the H2 preference schema without enabling Hibernate schema generation. */
    @Bean
    fun reactiveSchemaInitializer(
        @Qualifier("reactiveConnectionFactory") connectionFactory: ConnectionFactory,
    ): ConnectionFactoryInitializer =
        ConnectionFactoryInitializer().apply {
            setConnectionFactory(connectionFactory)
            setDatabasePopulator(
                ResourceDatabasePopulator(ClassPathResource("db/r2dbc/h2/V1__user_preferences.sql")),
            )
        }

    private companion object {
        const val DATABASE_FILE = "visual-agent"
    }
}
