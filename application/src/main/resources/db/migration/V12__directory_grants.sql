CREATE TABLE directory_grants (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    canonical_root TEXT UNIQUE,
    origin TEXT NOT NULL CHECK (origin IN ('SERVER', 'CLIENT')),
    mode TEXT NOT NULL CHECK (mode IN ('READ_ONLY', 'READ_WRITE')),
    client_binding_id TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (
        (origin = 'SERVER' AND canonical_root IS NOT NULL AND client_binding_id IS NULL) OR
        (origin = 'CLIENT' AND canonical_root IS NULL AND client_binding_id IS NOT NULL)
    )
);
