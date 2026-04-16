package com.visualagent.knowledge

import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

data class Memory(
    val id: String,
    val content: String,
    val tags: List<String>,
    val createdAt: Instant,
    val embedding: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Memory
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class ProjectKnowledge(
    val id: String,
    val projectPath: String,
    val name: String?,
    val description: String?,
    val summary: String?,
    val lastAccessed: Instant?,
)

class KnowledgeDb(private val dbPath: String = "./data/visual-agent.db") {

    private var connection: Connection? = null

    init {
        ensureDataDirectory()
        initDatabase()
    }

    private fun ensureDataDirectory() {
        val dataDir = java.io.File(dbPath).parentFile
        if (dataDir != null && !dataDir.exists()) {
            dataDir.mkdirs()
        }
    }

    private fun getConnection(): Connection {
        if (connection == null || connection!!.isClosed) {
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            connection!!.createStatement().execute("PRAGMA journal_mode=WAL")
        }
        return connection!!
    }

    private fun initDatabase() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS long_term_memory (
                    id TEXT PRIMARY KEY,
                    content TEXT NOT NULL,
                    embedding BLOB,
                    tags TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    access_count INTEGER DEFAULT 0,
                    last_accessed TIMESTAMP
                )
                """.trimIndent(),
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS project_knowledge (
                    id TEXT PRIMARY KEY,
                    project_path TEXT NOT NULL UNIQUE,
                    name TEXT,
                    description TEXT,
                    summary TEXT,
                    last_accessed TIMESTAMP
                )
                """.trimIndent(),
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS user_preferences (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    type TEXT DEFAULT 'string',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS conversation_history (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    metadata TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS todos (
                    id TEXT PRIMARY KEY,
                    description TEXT NOT NULL,
                    status TEXT DEFAULT 'PENDING',
                    priority TEXT DEFAULT 'MEDIUM',
                    assigned_agent_id TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    completed_at TIMESTAMP,
                    due_date TIMESTAMP
                )
                """.trimIndent(),
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS sub_agents (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    status TEXT DEFAULT 'IDLE',
                    current_task TEXT,
                    parent_agent_id TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )
        }
    }

    fun saveMemory(content: String, tags: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        val conn = getConnection()
        conn.prepareStatement(
            """
            INSERT INTO long_term_memory (id, content, tags, created_at)
            VALUES (?, ?, ?, ?)
        """,
        ).use { stmt ->
            stmt.setString(1, id)
            stmt.setString(2, content)
            stmt.setString(3, tags.joinToString(","))
            stmt.setString(4, Instant.now().toString())
            stmt.executeUpdate()
        }
        return id
    }

    fun searchMemories(query: String, limit: Int = 10): List<Memory> {
        val conn = getConnection()
        val memories = mutableListOf<Memory>()

        conn.prepareStatement(
            """
            SELECT * FROM long_term_memory 
            WHERE content LIKE ? OR tags LIKE ?
            ORDER BY created_at DESC
            LIMIT ?
        """,
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

    fun getPreference(key: String): String? {
        val conn = getConnection()
        conn.prepareStatement("SELECT value FROM user_preferences WHERE key = ?").use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getString("value") else null
            }
        }
    }

    fun setPreference(key: String, value: String) {
        val conn = getConnection()
        conn.prepareStatement(
            """
            INSERT OR REPLACE INTO user_preferences (key, value, updated_at)
            VALUES (?, ?, ?)
        """,
        ).use { stmt ->
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.setString(3, Instant.now().toString())
            stmt.executeUpdate()
        }
    }

    fun close() {
        connection?.close()
        connection = null
    }
}
