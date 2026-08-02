CREATE TABLE IF NOT EXISTS long_term_memory (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    embedding BLOB,
    tags TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    access_count INTEGER DEFAULT 0,
    last_accessed TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_knowledge (
    id TEXT PRIMARY KEY,
    project_path TEXT NOT NULL UNIQUE,
    name TEXT,
    description TEXT,
    summary TEXT,
    last_accessed TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_preferences (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    type TEXT DEFAULT 'string',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation_history (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conversation_history_session_created
    ON conversation_history (session_id, created_at DESC);

CREATE VIRTUAL TABLE IF NOT EXISTS conversation_history_fts USING fts5(
    id UNINDEXED,
    session_id UNINDEXED,
    content
);

CREATE TRIGGER IF NOT EXISTS trg_conversation_history_ai
AFTER INSERT ON conversation_history
BEGIN
    INSERT INTO conversation_history_fts(id, session_id, content)
    VALUES (new.id, new.session_id, new.content);
END;

CREATE TRIGGER IF NOT EXISTS trg_conversation_history_ad
AFTER DELETE ON conversation_history
BEGIN
    DELETE FROM conversation_history_fts WHERE id = old.id;
END;

CREATE TRIGGER IF NOT EXISTS trg_conversation_history_au
AFTER UPDATE ON conversation_history
BEGIN
    DELETE FROM conversation_history_fts WHERE id = old.id;
    INSERT INTO conversation_history_fts(id, session_id, content)
    VALUES (new.id, new.session_id, new.content);
END;

INSERT INTO conversation_history_fts(id, session_id, content)
SELECT ch.id, ch.session_id, ch.content
FROM conversation_history ch
WHERE NOT EXISTS (
    SELECT 1 FROM conversation_history_fts fts WHERE fts.id = ch.id
);

CREATE TABLE IF NOT EXISTS todos (
    id TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    status TEXT DEFAULT 'PENDING',
    priority TEXT DEFAULT 'MEDIUM',
    assigned_agent_id TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    due_date TIMESTAMP
);

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
);

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
);
