# Database Schema

## Overview

Visual Agent uses SQLite as an embedded database for:
- Long-term knowledge storage
- Project context
- User preferences
- Conversation history

## Schema

### long_term_memory

Stores long-term knowledge for RAG (Retrieval Augmented Generation).

```sql
CREATE TABLE long_term_memory (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    embedding BLOB,
    source TEXT,
    tags TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    access_count INTEGER DEFAULT 0,
    last_accessed TIMESTAMP
);

CREATE INDEX idx_memory_tags ON long_term_memory(tags);
CREATE INDEX idx_memory_created ON long_term_memory(created_at);
```

### project_knowledge

Project-specific knowledge and context.

```sql
CREATE TABLE project_knowledge (
    id TEXT PRIMARY KEY,
    project_path TEXT NOT NULL UNIQUE,
    name TEXT,
    description TEXT,
    content_hash TEXT,
    summary TEXT,
    file_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_indexed TIMESTAMP,
    last_accessed TIMESTAMP
);

CREATE INDEX idx_project_path ON project_knowledge(project_path);
```

### user_preferences

User settings and personalization.

```sql
CREATE TABLE user_preferences (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    type TEXT DEFAULT 'string',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Default values
INSERT INTO user_preferences (key, value, type) VALUES
    ('agent_name', 'Visual Agent', 'string'),
    ('agent_image', '', 'string'),
    ('theme', 'dark', 'string'),
    ('default_model', 'llama3.2', 'string'),
    ('default_provider', 'ollama_local', 'string');
```

### conversation_history

Complete conversation history.

```sql
CREATE TABLE conversation_history (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    parent_id TEXT,
    role TEXT NOT NULL, -- 'system', 'user', 'assistant'
    content TEXT NOT NULL,
    metadata TEXT, -- JSON for additional data
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES conversation_history(id)
);

CREATE INDEX idx_conversation_session ON conversation_history(session_id);
CREATE INDEX idx_conversation_created ON conversation_history(created_at);
```

### todos

Task management.

```sql
CREATE TABLE todos (
    id TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    status TEXT DEFAULT 'pending', -- 'pending', 'in_progress', 'completed', 'cancelled'
    priority TEXT DEFAULT 'medium', -- 'low', 'medium', 'high', 'urgent'
    assigned_agent_id TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    due_date TIMESTAMP,
    metadata TEXT -- JSON for additional data
);

CREATE INDEX idx_todos_status ON todos(status);
CREATE INDEX idx_todos_priority ON todos(priority);
```

### sub_agents

SubAgent information.

```sql
CREATE TABLE sub_agents (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT DEFAULT 'idle', -- 'idle', 'busy', 'offline'
    current_task TEXT,
    parent_agent_id TEXT,
    capabilities TEXT, -- JSON array
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agents_status ON sub_agents(status);
```

### tool_executions

Log for tool executions.

```sql
CREATE TABLE tool_executions (
    id TEXT PRIMARY KEY,
    tool_name TEXT NOT NULL,
    arguments TEXT, -- JSON
    result TEXT, -- JSON
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    execution_time_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tool_name ON tool_executions(tool_name);
CREATE INDEX idx_tool_created ON tool_executions(created_at);
```

## Kotlin DAO Implementation

### Database Helper

```kotlin
class DatabaseHelper(private val dbPath: String = "./data/visual-agent.db") {
    private val connection: Connection by lazy {
        DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }
    
    fun init() {
        connection.createStatement().use { stmt ->
            // Create all tables
            stmt.execute(longTermMemoryTable)
            stmt.execute(projectKnowledgeTable)
            stmt.execute(userPreferencesTable)
            stmt.execute(conversationHistoryTable)
            stmt.execute(todosTable)
            stmt.execute(subAgentsTable)
            stmt.execute(toolExecutionsTable)
        }
    }
}
```

### Knowledge Repository

```kotlin
class KnowledgeRepository(private val db: DatabaseHelper) {
    
    suspend fun saveMemory(content: String, tags: List<String>): String {
        val id = UUID.randomUUID().toString()
        db.connection.prepareStatement("""
            INSERT INTO long_term_memory (id, content, tags)
            VALUES (?, ?, ?)
        """).use { stmt ->
            stmt.setString(1, id)
            stmt.setString(2, content)
            stmt.setString(3, tags.joinToString(","))
            stmt.executeUpdate()
        }
        return id
    }
    
    suspend fun searchMemories(query: String, limit: Int = 10): List<Memory> {
        // TODO: Implement embedding-based search
        return db.connection.prepareStatement("""
            SELECT * FROM long_term_memory 
            WHERE content LIKE ? OR tags LIKE ?
            ORDER BY created_at DESC
            LIMIT ?
        """).use { stmt ->
            stmt.setString(1, "%$query%")
            stmt.setString(2, "%$query%")
            stmt.setInt(3, limit)
            stmt.executeQuery().map { rs ->
                Memory(
                    id = rs.getString("id"),
                    content = rs.getString("content"),
                    tags = rs.getString("tags").split(","),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            }
        }
    }
    
    suspend fun getPreference(key: String): String? {
        return db.connection.prepareStatement("""
            SELECT value FROM user_preferences WHERE key = ?
        """).use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().takeIf { it.next() }?.getString("value")
        }
    }
    
    suspend fun setPreference(key: String, value: String) {
        db.connection.prepareStatement("""
            INSERT OR REPLACE INTO user_preferences (key, value, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
        """).use { stmt ->
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.executeUpdate()
        }
    }
}
```

## Migrations

For future schema changes:

```sql
-- migrations/002_add_embedding_index.sql
CREATE INDEX idx_memory_embedding ON long_term_memory(embedding);

-- migrations/003_add_agent_metadata.sql
ALTER TABLE sub_agents ADD COLUMN metadata TEXT;
```

## Backup

```bash
# Backup database
cp data/visual-agent.db data/visual-agent.db.backup

# Restore backup
cp data/visual-agent.db.backup data/visual-agent.db
```

## Performance Optimization

```sql
-- WAL mode for better performance
PRAGMA journal_mode=WAL;

-- Increase cache size
PRAGMA cache_size=-64000; -- 64MB

-- Enable foreign keys
PRAGMA foreign_keys=ON;
```
