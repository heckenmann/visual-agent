package de.heckenmann.visualagent.knowledge

import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.DriverManager

/**
 * Manages the shared SQLite connection lifecycle for persistence beans.
 *
 * @property dbPath SQLite file path or `jdbc:sqlite:` URL
 */
@Component
internal class KnowledgeConnectionManager(
    @Value("\${visual-agent.db.path:./data/visual-agent.db}")
    private val dbPath: String,
) {
    private var connection: Connection? = null

    init {
        ensureDataDirectory()
    }

    /**
     * Returns a reusable open SQLite connection with required PRAGMAs applied.
     *
     * @return Open JDBC connection
     */
    fun getConnection(): Connection {
        if (connection == null || connection!!.isClosed) {
            connection = DriverManager.getConnection(resolveJdbcUrl(dbPath))
            connection!!.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA busy_timeout=5000")
            }
        }
        return connection!!
    }

    @PreDestroy
    fun close() {
        connection?.close()
        connection = null
    }

    private fun ensureDataDirectory() {
        val dataDir = java.io.File(dbPath).parentFile
        if (dataDir != null && !dataDir.exists()) {
            dataDir.mkdirs()
        }
    }

    private fun resolveJdbcUrl(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("jdbc:sqlite:")) pathOrUrl else "jdbc:sqlite:$pathOrUrl"
}
