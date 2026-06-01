package de.heckenmann.visualagent.knowledge

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Represents ConversationDao.
 */
@Component
class ConversationDao(
    private val connectionProvider: ConnectionProvider,
) {
    /**
     * Saves one conversation message.
     *
     * @param sessionId Logical session identifier
     * @param role Message role
     * @param content Message content
     * @param metadata Optional serialized metadata
     * @return Created message ID
     */
    fun saveConversationMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        connectionProvider
            .get()
            .prepareStatement(
                """
                INSERT INTO conversation_history (id, session_id, role, content, metadata, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, sessionId)
                stmt.setString(3, role)
                stmt.setString(4, content)
                stmt.setString(5, metadata)
                stmt.setString(6, Instant.now().toString())
                stmt.executeUpdate()
            }
        return id
    }

    /**
     * Loads recent messages for a session in chronological order.
     *
     * @param sessionId Logical session identifier
     * @param limit Maximum rows
     * @return Message rows
     */
    fun getConversationMessages(
        sessionId: String,
        limit: Int = 500,
    ): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        connectionProvider
            .get()
            .prepareStatement(
                """
                SELECT id, role, content, metadata, created_at
                FROM conversation_history
                WHERE session_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) rows += rs.toConversationRow()
                }
            }
        return rows.reversed()
    }

    /**
     * Loads one page of older messages.
     *
     * @param sessionId Logical session identifier
     * @param limit Maximum rows
     * @param offset Number of newest rows to skip
     * @return Message rows in chronological order
     */
    fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        connectionProvider
            .get()
            .prepareStatement(
                """
                SELECT id, role, content, metadata, created_at
                FROM conversation_history
                WHERE session_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                OFFSET ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setInt(2, limit.coerceAtLeast(1))
                stmt.setInt(3, offset.coerceAtLeast(0))
                stmt.executeQuery().use { rs ->
                    while (rs.next()) rows += rs.toConversationRow()
                }
            }
        return rows.reversed()
    }

    /**
     * Searches persisted messages by FTS, falling back to LIKE if FTS fails.
     *
     * @param sessionId Logical session identifier
     * @param query Search query
     * @param limit Maximum rows
     * @return Matching rows ordered newest first
     */
    fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int = 20,
    ): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        val safeLimit = limit.coerceIn(1, 200)
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return rows
        runCatching {
            searchConversationFts(sessionId, normalizedQuery, safeLimit, rows)
        }.onFailure {
            searchConversationLike(sessionId, normalizedQuery, safeLimit, rows)
        }
        return rows
    }

    /**
     * Deletes all messages for a session.
     *
     * @param sessionId Logical session identifier
     * @return Deleted row count
     */
    fun deleteConversationMessages(sessionId: String): Int {
        connectionProvider.get().prepareStatement("DELETE FROM conversation_history WHERE session_id = ?").use { stmt ->
            stmt.setString(1, sessionId)
            return stmt.executeUpdate()
        }
    }

    private fun searchConversationFts(
        sessionId: String,
        query: String,
        limit: Int,
        rows: MutableList<Map<String, String>>,
    ) {
        connectionProvider.get().prepareStatement(FTS_QUERY).use { stmt ->
            stmt.setString(1, sessionId)
            stmt.setString(2, query)
            stmt.setInt(3, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) rows += rs.toConversationRow()
            }
        }
    }

    private fun searchConversationLike(
        sessionId: String,
        query: String,
        limit: Int,
        rows: MutableList<Map<String, String>>,
    ) {
        connectionProvider.get().prepareStatement(LIKE_QUERY).use { stmt ->
            stmt.setString(1, sessionId)
            stmt.setString(2, "%${query.lowercase()}%")
            stmt.setInt(3, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) rows += rs.toConversationRow()
            }
        }
    }

    private fun java.sql.ResultSet.toConversationRow(): Map<String, String> =
        mapOf(
            "id" to getString("id"),
            "role" to getString("role"),
            "content" to getString("content"),
            "metadata" to (getString("metadata") ?: ""),
            "createdAt" to getString("created_at"),
        )

    private companion object {
        private val FTS_QUERY =
            """
            SELECT ch.id, ch.role, ch.content, ch.metadata, ch.created_at
            FROM conversation_history_fts fts
            JOIN conversation_history ch ON ch.id = fts.id
            WHERE fts.session_id = ? AND fts.content MATCH ?
            ORDER BY ch.created_at DESC, ch.id DESC
            LIMIT ?
            """.trimIndent()

        private val LIKE_QUERY =
            """
            SELECT id, role, content, metadata, created_at
            FROM conversation_history
            WHERE session_id = ? AND lower(content) LIKE ?
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """.trimIndent()
    }
}
