CREATE TABLE IF NOT EXISTS workspace_files (
    id TEXT PRIMARY KEY,
    relative_path TEXT NOT NULL UNIQUE,
    original_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    sha256 TEXT NOT NULL,
    extracted_text TEXT,
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workspace_files_imported
    ON workspace_files (imported_at DESC);
