package de.heckenmann.visualagent.knowledge

import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the opt-in file-backed H2 R2DBC foundation without starting the legacy JPA context. */
class ReactiveKnowledgePersistenceConfigTest {
    @Test
    fun `file-backed H2 R2DBC client persists data across connections`() {
        val serverDataRoot = Files.createTempDirectory("visual-agent-r2dbc")
        val configuration = ReactiveKnowledgePersistenceConfig()
        val connectionFactory = configuration.connectionFactory(serverDataRoot)
        val client = DatabaseClient.create(connectionFactory)

        client
            .sql("CREATE TABLE reactive_probe (id VARCHAR(64) PRIMARY KEY, content VARCHAR(255) NOT NULL)")
            .fetch()
            .rowsUpdated()
            .block()
        client
            .sql("INSERT INTO reactive_probe (id, content) VALUES (:id, :content)")
            .bind("id", "first")
            .bind("content", "persisted")
            .fetch()
            .rowsUpdated()
            .block()

        val result =
            client
                .sql("SELECT content FROM reactive_probe WHERE id = :id")
                .bind("id", "first")
                .map { row, _ -> row.get("content", String::class.java) ?: error("Missing probe content") }
                .one()
                .block()

        assertEquals("persisted", result)
        assertTrue(Files.exists(serverDataRoot.resolve("visual-agent.mv.db")))
        close(connectionFactory)

        val reopenedFactory = configuration.connectionFactory(serverDataRoot)
        val reopenedClient = DatabaseClient.create(reopenedFactory)
        val reopened =
            reopenedClient
                .sql("SELECT content FROM reactive_probe WHERE id = :id")
                .bind("id", "first")
                .map { row, _ -> row.get("content", String::class.java) ?: error("Missing probe content") }
                .one()
                .block()

        assertEquals("persisted", reopened)
        close(reopenedFactory)
    }

    private fun close(connectionFactory: io.r2dbc.spi.ConnectionFactory) {
        reactor.core.publisher.Mono
            .from(connectionFactory.create())
            .flatMap { connection ->
                reactor.core.publisher.Mono
                    .from(connection.close())
            }.block()
    }
}
