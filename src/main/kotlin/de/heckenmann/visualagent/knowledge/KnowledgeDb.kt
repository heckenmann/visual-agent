package de.heckenmann.visualagent.knowledge

import org.springframework.stereotype.Repository
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

@Repository
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
            connection!!.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA busy_timeout=5000")
            }
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
                    config TEXT NOT NULL DEFAULT '{"timeout":60,"maxRetries":3,"memoryLimitMb":512}',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

    /**
     * Save a structured knowledge record as JSON in the long_term_memory table.
     * This is a best-effort helper for SubAgents to store summaries and next steps.
     */
    fun saveStructuredKnowledge(subject: String, summary: String, nextSteps: String?): String {
        val payload = "{\"subject\":\"${subject}\",\"summary\":\"${summary.replace("\"","\\\"")}\",\"next_steps\":\"${nextSteps?.replace("\"","\\\"") ?: ""}\"}"
        return saveMemory(payload, listOf(subject))
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

    /**
     * Save or update a SubAgent in the database.
     *
     * @param id Agent ID
     * @param name Agent name
     * @param role Agent role
     * @param status Agent status (IDLE, BUSY, OFFLINE)
     * @param currentTask Current task description
     * @param parentAgentId Optional parent agent ID
     * @param configJson JSON serialized AgentConfig
     * @return true if insert, false if update
     */
    fun saveAgent(
        id: String,
        name: String,
        role: String,
        status: String,
        currentTask: String? = null,
        parentAgentId: String? = null,
        configJson: String,
    ): Boolean {
        val conn = getConnection()
        // Check if agent already exists
        val exists = conn.prepareStatement("SELECT 1 FROM sub_agents WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { it.next() }
        }

        return if (exists) {
            conn.prepareStatement(
                """
                UPDATE sub_agents 
                SET name = ?, role = ?, status = ?, current_task = ?, parent_agent_id = ?, config = ?, updated_at = ?
                WHERE id = ?
            """,
            ).use { stmt ->
                stmt.setString(1, name)
                stmt.setString(2, role)
                stmt.setString(3, status)
                stmt.setString(4, currentTask)
                stmt.setString(5, parentAgentId)
                stmt.setString(6, configJson)
                stmt.setString(7, Instant.now().toString())
                stmt.setString(8, id)
                stmt.executeUpdate() > 0
            }
            false
        } else {
            conn.prepareStatement(
                """
                INSERT INTO sub_agents (id, name, role, status, current_task, parent_agent_id, config, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, name)
                stmt.setString(3, role)
                stmt.setString(4, status)
                stmt.setString(5, currentTask)
                stmt.setString(6, parentAgentId)
                stmt.setString(7, configJson)
                stmt.setString(8, Instant.now().toString())
                stmt.setString(9, Instant.now().toString())
                stmt.executeUpdate() > 0
            }
            true
        }
    }

    /**
     * Retrieve a SubAgent by ID.
     */
    fun getAgent(id: String): Map<String, Any>? {
        val conn = getConnection()
        conn.prepareStatement("SELECT * FROM sub_agents WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) {
                    mapOf(
                        "id" to rs.getString("id"),
                        "name" to rs.getString("name"),
                        "role" to rs.getString("role"),
                        "status" to rs.getString("status"),
                        "currentTask" to (rs.getString("current_task") ?: ""),
                        "parentAgentId" to (rs.getString("parent_agent_id") ?: ""),
                        "config" to rs.getString("config"),
                        "createdAt" to rs.getString("created_at"),
                        "updatedAt" to rs.getString("updated_at"),
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * List all SubAgents, optionally filtered by status.
     */
    fun listAgents(status: String? = null): List<Map<String, Any>> {
        val conn = getConnection()
        val query = if (status != null) {
            "SELECT * FROM sub_agents WHERE status = ? ORDER BY created_at DESC"
        } else {
            "SELECT * FROM sub_agents ORDER BY created_at DESC"
        }

        val agents = mutableListOf<Map<String, Any>>()
        conn.prepareStatement(query).use { stmt ->
            if (status != null) stmt.setString(1, status)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    agents.add(
                        mapOf(
                            "id" to rs.getString("id"),
                            "name" to rs.getString("name"),
                            "role" to rs.getString("role"),
                            "status" to rs.getString("status"),
                            "currentTask" to (rs.getString("current_task") ?: ""),
                            "parentAgentId" to (rs.getString("parent_agent_id") ?: ""),
                            "config" to rs.getString("config"),
                            "createdAt" to rs.getString("created_at"),
                            "updatedAt" to rs.getString("updated_at"),
                        ),
                    )
                }
            }
        }
        return agents
    }

    /**
     * Delete a SubAgent by ID.
     */
    fun deleteAgent(id: String): Boolean {
        val conn = getConnection()
        conn.prepareStatement("DELETE FROM sub_agents WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() > 0
        }
    }

    /**
     * Update agent status (e.g., IDLE → BUSY).
     */
    fun updateAgentStatus(id: String, status: String, currentTask: String? = null): Boolean {
        val conn = getConnection()
        conn.prepareStatement(
            """
            UPDATE sub_agents 
            SET status = ?, current_task = ?, updated_at = ?
            WHERE id = ?
        """,
        ).use { stmt ->
            stmt.setString(1, status)
            stmt.setString(2, currentTask)
            stmt.setString(3, Instant.now().toString())
            stmt.setString(4, id)
            return stmt.executeUpdate() > 0
        }
    }

    fun close() {
        connection?.close()
        connection = null
    }
}
