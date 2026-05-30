package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.SubAgentToolConfig
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
class KnowledgeDb(
    private val dbPath: String = "./data/visual-agent.db",
) {
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
            connection = DriverManager.getConnection(resolveJdbcUrl(dbPath))
            connection!!.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA busy_timeout=5000")
            }
        }
        return connection!!
    }

    private fun resolveJdbcUrl(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("jdbc:sqlite:")) pathOrUrl else "jdbc:sqlite:$pathOrUrl"

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

            // Supports fast session-based paging ordered by creation time.
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_conversation_history_session_created
                ON conversation_history (session_id, created_at DESC)
                """.trimIndent(),
            )

            // Full-text index for scalable keyword search in conversation content.
            stmt.execute(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS conversation_history_fts USING fts5(
                    id UNINDEXED,
                    session_id UNINDEXED,
                    content
                )
                """.trimIndent(),
            )

            stmt.execute(
                """
                CREATE TRIGGER IF NOT EXISTS trg_conversation_history_ai
                AFTER INSERT ON conversation_history
                BEGIN
                    INSERT INTO conversation_history_fts(id, session_id, content)
                    VALUES (new.id, new.session_id, new.content);
                END
                """.trimIndent(),
            )

            stmt.execute(
                """
                CREATE TRIGGER IF NOT EXISTS trg_conversation_history_ad
                AFTER DELETE ON conversation_history
                BEGIN
                    DELETE FROM conversation_history_fts WHERE id = old.id;
                END
                """.trimIndent(),
            )

            stmt.execute(
                """
                CREATE TRIGGER IF NOT EXISTS trg_conversation_history_au
                AFTER UPDATE ON conversation_history
                BEGIN
                    DELETE FROM conversation_history_fts WHERE id = old.id;
                    INSERT INTO conversation_history_fts(id, session_id, content)
                    VALUES (new.id, new.session_id, new.content);
                END
                """.trimIndent(),
            )

            // Backfill for existing rows created before FTS setup.
            stmt.execute(
                """
                INSERT INTO conversation_history_fts(id, session_id, content)
                SELECT ch.id, ch.session_id, ch.content
                FROM conversation_history ch
                WHERE NOT EXISTS (
                    SELECT 1 FROM conversation_history_fts fts WHERE fts.id = ch.id
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

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS sub_agent_configs (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    model TEXT NOT NULL,
                    system_prompt TEXT NOT NULL DEFAULT '',
                    tools TEXT NOT NULL DEFAULT '[]',
                    max_turns INTEGER NOT NULL DEFAULT 5,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )
        }
    }

    fun saveMemory(
        content: String,
        tags: List<String> = emptyList(),
    ): String {
        val id = UUID.randomUUID().toString()
        val conn = getConnection()
        conn
            .prepareStatement(
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
    fun saveStructuredKnowledge(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): String {
        val payload = "{\"subject\":\"${subject}\",\"summary\":\"${summary.replace(
            "\"",
            "\\\"",
        )}\",\"next_steps\":\"${nextSteps?.replace("\"","\\\"") ?: ""}\"}"
        return saveMemory(payload, listOf(subject))
    }

    fun searchMemories(
        query: String,
        limit: Int = 10,
    ): List<Memory> {
        val conn = getConnection()
        val memories = mutableListOf<Memory>()

        conn
            .prepareStatement(
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

    fun setPreference(
        key: String,
        value: String,
    ) {
        val conn = getConnection()
        conn
            .prepareStatement(
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
     * Persist one chat message in the conversation history table.
     *
     * @param sessionId Logical conversation/session identifier
     * @param role Message role (user, assistant, system)
     * @param content Message body
     * @param metadata Optional serialized metadata payload
     * @return Created message ID
     */
    fun saveConversationMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val conn = getConnection()
        conn
            .prepareStatement(
                """
            INSERT INTO conversation_history (id, session_id, role, content, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """,
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
     * Load conversation history for a session in chronological order.
     *
     * @param sessionId Logical conversation/session identifier
     * @param limit Maximum number of rows to load (newest rows are selected first and returned chronologically)
     */
    fun getConversationMessages(
        sessionId: String,
        limit: Int = 500,
    ): List<Map<String, String>> {
        val conn = getConnection()
        val rows = mutableListOf<Map<String, String>>()
        conn
            .prepareStatement(
                """
            SELECT id, role, content, metadata, created_at
            FROM conversation_history
            WHERE session_id = ?
            ORDER BY created_at DESC
            LIMIT ?
        """,
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        rows.add(
                            mapOf(
                                "id" to rs.getString("id"),
                                "role" to rs.getString("role"),
                                "content" to rs.getString("content"),
                                "metadata" to (rs.getString("metadata") ?: ""),
                                "createdAt" to rs.getString("created_at"),
                            ),
                        )
                    }
                }
            }
        return rows.reversed()
    }

    /**
     * Load one conversation page for a session in chronological order.
     *
     * @param sessionId Logical conversation/session identifier
     * @param limit Maximum number of rows to load
     * @param offset Number of newest rows to skip
     * @return Paged conversation messages ordered from oldest to newest in that page
     */
    fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<Map<String, String>> {
        val conn = getConnection()
        val rows = mutableListOf<Map<String, String>>()
        conn
            .prepareStatement(
                """
            SELECT id, role, content, metadata, created_at
            FROM conversation_history
            WHERE session_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            OFFSET ?
        """,
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setInt(2, limit.coerceAtLeast(1))
                stmt.setInt(3, offset.coerceAtLeast(0))
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        rows.add(
                            mapOf(
                                "id" to rs.getString("id"),
                                "role" to rs.getString("role"),
                                "content" to rs.getString("content"),
                                "metadata" to (rs.getString("metadata") ?: ""),
                                "createdAt" to rs.getString("created_at"),
                            ),
                        )
                    }
                }
            }
        return rows.reversed()
    }

    /**
     * Search conversation messages by keyword for a given session.
     *
     * @param sessionId Logical conversation/session identifier
     * @param query Case-insensitive keyword fragment
     * @param limit Maximum number of matches
     * @return Matching messages ordered from newest to oldest
     */
    fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int = 20,
    ): List<Map<String, String>> {
        val conn = getConnection()
        val rows = mutableListOf<Map<String, String>>()
        val safeLimit = limit.coerceIn(1, 200)
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return rows
        runCatching {
            conn
                .prepareStatement(
                    """
                SELECT ch.id, ch.role, ch.content, ch.metadata, ch.created_at
                FROM conversation_history_fts fts
                JOIN conversation_history ch ON ch.id = fts.id
                WHERE fts.session_id = ? AND fts.content MATCH ?
                ORDER BY ch.created_at DESC
                LIMIT ?
            """,
                ).use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.setString(2, normalizedQuery)
                    stmt.setInt(3, safeLimit)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            rows.add(
                                mapOf(
                                    "id" to rs.getString("id"),
                                    "role" to rs.getString("role"),
                                    "content" to rs.getString("content"),
                                    "metadata" to (rs.getString("metadata") ?: ""),
                                    "createdAt" to rs.getString("created_at"),
                                ),
                            )
                        }
                    }
                }
        }.onFailure {
            conn
                .prepareStatement(
                    """
                SELECT id, role, content, metadata, created_at
                FROM conversation_history
                WHERE session_id = ? AND lower(content) LIKE ?
                ORDER BY created_at DESC
                LIMIT ?
            """,
                ).use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.setString(2, "%${normalizedQuery.lowercase()}%")
                    stmt.setInt(3, safeLimit)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            rows.add(
                                mapOf(
                                    "id" to rs.getString("id"),
                                    "role" to rs.getString("role"),
                                    "content" to rs.getString("content"),
                                    "metadata" to (rs.getString("metadata") ?: ""),
                                    "createdAt" to rs.getString("created_at"),
                                ),
                            )
                        }
                    }
                }
        }
        return rows
    }

    /**
     * Delete all conversation messages for a session.
     *
     * @param sessionId Logical conversation/session identifier
     * @return Number of deleted rows
     */
    fun deleteConversationMessages(sessionId: String): Int {
        val conn = getConnection()
        conn.prepareStatement("DELETE FROM conversation_history WHERE session_id = ?").use { stmt ->
            stmt.setString(1, sessionId)
            return stmt.executeUpdate()
        }
    }

    /**
     * Save or update one todo row.
     *
     * @param todo Todo to persist
     */
    fun saveTodo(todo: Todo) {
        val conn = getConnection()
        conn
            .prepareStatement(
                """
                INSERT INTO todos (id, description, status, priority, assigned_agent_id, created_at, completed_at, due_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    description = excluded.description,
                    status = excluded.status,
                    priority = excluded.priority,
                    assigned_agent_id = excluded.assigned_agent_id,
                    created_at = excluded.created_at,
                    completed_at = excluded.completed_at,
                    due_date = excluded.due_date
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, todo.id)
                stmt.setString(2, todo.description)
                stmt.setString(3, todo.status.name)
                stmt.setString(4, todo.priority.name)
                stmt.setString(5, todo.assignedAgentId)
                stmt.setString(6, todo.createdAt.toString())
                stmt.setString(7, todo.completedAt?.toString())
                stmt.setString(8, todo.dueDate?.toString())
                stmt.executeUpdate()
            }
    }

    /**
     * Load all todos ordered by creation timestamp.
     *
     * @return Persisted todos
     */
    fun listTodos(): List<Todo> {
        val conn = getConnection()
        val todos = mutableListOf<Todo>()
        conn
            .prepareStatement(
                """
                SELECT id, description, status, priority, assigned_agent_id, created_at, completed_at, due_date
                FROM todos
                ORDER BY created_at ASC
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val createdAt = runCatching { Instant.parse(rs.getString("created_at")) }.getOrElse { Instant.now() }
                        val completedAt = rs.getString("completed_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        val dueDate = rs.getString("due_date")?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        todos +=
                            Todo(
                                id = rs.getString("id"),
                                description = rs.getString("description"),
                                status = runCatching { TodoStatus.valueOf(rs.getString("status")) }.getOrDefault(TodoStatus.PENDING),
                                priority = runCatching { TodoPriority.valueOf(rs.getString("priority")) }.getOrDefault(TodoPriority.MEDIUM),
                                assignedAgentId = rs.getString("assigned_agent_id"),
                                createdAt = createdAt,
                                completedAt = completedAt,
                                dueDate = dueDate,
                            )
                    }
                }
            }
        return todos
    }

    /**
     * Delete one todo row.
     *
     * @param todoId Todo ID to remove
     */
    fun deleteTodo(todoId: String) {
        val conn = getConnection()
        conn.prepareStatement("DELETE FROM todos WHERE id = ?").use { stmt ->
            stmt.setString(1, todoId)
            stmt.executeUpdate()
        }
    }

    /**
     * Remove all todos from storage.
     */
    fun clearTodos() {
        val conn = getConnection()
        conn.prepareStatement("DELETE FROM todos").use { stmt ->
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
        val exists =
            conn.prepareStatement("SELECT 1 FROM sub_agents WHERE id = ?").use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { it.next() }
            }

        return if (exists) {
            conn
                .prepareStatement(
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
            conn
                .prepareStatement(
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
        val query =
            if (status != null) {
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
    fun updateAgentStatus(
        id: String,
        status: String,
        currentTask: String? = null,
    ): Boolean {
        val conn = getConnection()
        conn
            .prepareStatement(
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

    /**
     * Save or update an agent tool configuration.
     *
     * @param id Stable configuration ID
     * @param name Display name
     * @param description Description of the agent capability
     * @param model Preferred model name
     * @param systemPrompt System prompt used by this config
     * @param toolsJson JSON encoded list of tool IDs
     * @param maxTurns Maximum loop turns
     * @param enabled Whether the config is enabled
     */
    fun saveSubAgentConfig(
        id: String,
        name: String,
        description: String,
        model: String,
        systemPrompt: String,
        toolsJson: String,
        maxTurns: Int,
        enabled: Boolean,
    ) {
        val conn = getConnection()
        conn
            .prepareStatement(
                """
                INSERT OR REPLACE INTO sub_agent_configs
                    (id, name, description, model, system_prompt, tools, max_turns, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM sub_agent_configs WHERE id = ?), ?))
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, name)
                stmt.setString(3, description)
                stmt.setString(4, model)
                stmt.setString(5, systemPrompt)
                stmt.setString(6, toolsJson)
                stmt.setInt(7, maxTurns)
                stmt.setInt(8, if (enabled) 1 else 0)
                stmt.setString(9, id)
                stmt.setString(10, Instant.now().toString())
                stmt.executeUpdate()
            }
    }

    /**
     * Load one agent tool configuration.
     *
     * @param id Stable configuration ID
     * @return Configuration if present
     */
    fun getSubAgentConfig(id: String): SubAgentToolConfig? {
        val conn = getConnection()
        conn.prepareStatement("SELECT * FROM sub_agent_configs WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                return SubAgentToolConfig(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                    model = rs.getString("model"),
                    systemPrompt = rs.getString("system_prompt"),
                    tools =
                        runCatching {
                            Json.decodeFromString<List<String>>(rs.getString("tools"))
                        }.getOrElse { emptyList() },
                    maxTurns = rs.getInt("max_turns"),
                    enabled = rs.getInt("enabled") == 1,
                )
            }
        }
    }

    /**
     * List all persisted agent tool configurations.
     *
     * @return Configurations ordered by ID
     */
    fun listSubAgentConfigs(): List<SubAgentToolConfig> {
        val conn = getConnection()
        val configs = mutableListOf<SubAgentToolConfig>()
        conn.prepareStatement("SELECT id FROM sub_agent_configs ORDER BY id").use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    getSubAgentConfig(rs.getString("id"))?.let(configs::add)
                }
            }
        }
        return configs
    }

    fun close() {
        connection?.close()
        connection = null
    }
}
