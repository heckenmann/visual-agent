CREATE TABLE IF NOT EXISTS deleted_todos (
    id TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    status TEXT NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    assigned_agent_id TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    due_date TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_deleted_todos_updated
    ON deleted_todos (updated_at DESC, id DESC);
