package de.heckenmann.visualagent.knowledge

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Represents MemoryDao.
 */
@Component
class MemoryDao(
    private val connectionProvider: ConnectionProvider,
) {
    /**
     * Stores a memory row.
     *
     * @param content Memory content
     * @param tags Search tags
     * @return Created memory ID
     */
    fun saveMemory(
        content: String,
        tags: List<String> = emptyList(),
    ): String {
        val id = UUID.randomUUID().toString()
        connectionProvider
            .get()
            .prepareStatement(
                """
                INSERT INTO long_term_memory (id, content, tags, created_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, content)
                stmt.setString(3, tags.joinToString(","))
                stmt.setString(4, Instant.now().toString())
                stmt.executeUpdate()
            }
        return id
    }

    /**
     * Saves a structured knowledge payload as JSON in memory storage.
     *
     * @param subject Knowledge subject
     * @param summary Summary text
     * @param nextSteps Optional next-step text
     * @return Created memory ID
     */
    fun saveStructuredKnowledge(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): String {
        val payload =
            "{\"subject\":\"$subject\",\"summary\":\"${summary.replace("\"", "\\\"")}\",\"next_steps\":\"${
                nextSteps?.replace("\"", "\\\"") ?: ""
            }\"}"
        return saveMemory(payload, listOf(subject))
    }

    /**
     * Searches memory content and tags by SQL LIKE.
     *
     * @param query Search fragment
     * @param limit Maximum rows
     * @return Matching memory rows
     */
    fun searchMemories(
        query: String,
        limit: Int = 10,
    ): List<Memory> {
        val memories = mutableListOf<Memory>()
        connectionProvider
            .get()
            .prepareStatement(
                """
                SELECT * FROM long_term_memory
                WHERE content LIKE ? OR tags LIKE ?
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, "%$query%")
                stmt.setString(2, "%$query%")
                stmt.setInt(3, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        memories.add(
                            Memory(
                                id = rs.getString("id"),
                                content = rs.getString("content"),
                                tags = rs.getString("tags").split(",").filter { it.isNotBlank() },
                                createdAt = Instant.parse(rs.getString("created_at")),
                            ),
                        )
                    }
                }
            }
        return memories
    }
}
