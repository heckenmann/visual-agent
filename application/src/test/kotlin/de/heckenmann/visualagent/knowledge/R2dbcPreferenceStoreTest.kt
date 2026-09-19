package de.heckenmann.visualagent.knowledge

import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Verifies reactive preference CRUD, transaction rollback, and file-backed restart behavior. */
class R2dbcPreferenceStoreTest {
    @Test
    fun `preference values survive restart and updates are transactional`() {
        val serverDataRoot = Files.createTempDirectory("visual-agent-r2dbc-preferences")
        val configuration = ReactiveKnowledgePersistenceConfig()
        val connectionFactory = configuration.connectionFactory(serverDataRoot)
        val store = createStore(connectionFactory)
        val client = DatabaseClient.create(connectionFactory)

        client
            .sql(
                """
                CREATE TABLE user_preferences (
                    preference_key VARCHAR(512) PRIMARY KEY,
                    preference_value VARCHAR(1000000) NOT NULL,
                    preference_type VARCHAR(64) NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """.trimIndent(),
            ).fetch()
            .rowsUpdated()
            .block()

        store.setPreference("ui.theme", "dark").block()
        assertEquals("dark", store.getPreference("ui.theme").block())

        val transactionOperator = TransactionalOperator.create(R2dbcTransactionManager(connectionFactory))
        assertFailsInTransaction(transactionOperator, client)
        assertEquals("dark", store.getPreference("ui.theme").block())

        close(connectionFactory)
        val reopenedFactory = configuration.connectionFactory(serverDataRoot)
        val reopenedStore = createStore(reopenedFactory)
        assertEquals("dark", reopenedStore.getPreference("ui.theme").block())
        assertNull(reopenedStore.getPreference("missing").block())
        close(reopenedFactory)
    }

    private fun createStore(connectionFactory: ConnectionFactory): ReactivePreferenceStore {
        val transactionManager = R2dbcTransactionManager(connectionFactory)
        return R2dbcPreferenceStore(
            databaseClient = DatabaseClient.create(connectionFactory),
            transactionOperator = TransactionalOperator.create(transactionManager),
        )
    }

    private fun assertFailsInTransaction(
        operator: TransactionalOperator,
        client: DatabaseClient,
    ) {
        assertFailsWith<IllegalStateException> {
            operator
                .transactional(
                    client
                        .sql(
                            """
                            UPDATE user_preferences
                            SET preference_value = 'corrupted'
                            WHERE preference_key = 'ui.theme'
                            """.trimIndent(),
                        ).fetch()
                        .rowsUpdated()
                        .then(Mono.error(IllegalStateException("rollback"))),
                ).block()
        }
    }

    private fun close(connectionFactory: ConnectionFactory) {
        Mono
            .from(connectionFactory.create())
            .flatMap { connection -> Mono.from(connection.close()) }
            .block()
    }
}
