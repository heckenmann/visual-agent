# Database Schema

## Overview

Visual Agent uses SQLite through Spring Data JPA repositories and Flyway migrations.

Runtime defaults:

- Server data root from the platform-specific per-user application-data directory, in the
  `server/` namespace.
- Database: `<server-data-root>/visual-agent.db`
- `PRAGMA journal_mode=WAL`
- `PRAGMA busy_timeout=5000`
- Hibernate schema validation in production, migration-driven schema creation through Flyway

The application uses DB-first reads for conversation, todos, and related runtime context.
Managed workspace files are stored on disk below the resolved server data root at
`<server-data-root>/workspace/`.

The desktop client has a separate client-local bootstrap/configuration root. Its versioned
`startup-servers.json` contains only Visual Agent server bookmarks and the last selection. It is
loaded before any server connection and is never stored in this database.

For a packaged or standalone server, the default locations are resolved by the server process:

- Linux: `$XDG_DATA_HOME/Visual Agent/server/`, or `~/.local/share/Visual Agent/server/`
- macOS: `~/Library/Application Support/Visual Agent/server/`
- Windows: `%LOCALAPPDATA%/Visual Agent/server/`

Before a connection exists, the desktop client stores `startup-servers.json` in the platform
config directory: Linux `$XDG_CONFIG_HOME/Visual Agent/` (fallback `~/.config/Visual Agent/`),
macOS `~/Library/Preferences/Visual Agent/`, or Windows `%LOCALAPPDATA%/Visual Agent/`.

Set `visual-agent.server.data-root` to override the server root, or set the more specific
`visual-agent.db.path` to override only the database file. File-backed overrides must be absolute.
These values are server-side and are never supplied by a remote UI. Gradle development tasks explicitly set the repository-local
`data/visual-agent.db` path; this is a development override, not the packaged default.

## Active Tables

### `conversation_history`

Stores persisted chat timeline, including assistant/tool-related messages.

Used for:
- initial history load (limited page)
- incremental "load older" behavior
- keyword search via history tool

The table is backed by an FTS5 companion table, `conversation_history_fts`, and triggers keep the search index synchronized.

### `todos`

Stores persisted todo items.

Used for:
- session control/todo panel display
- model context prompt summary
- todo tool actions (`list`, `count`, `add`, `update`, `complete`, `cancel`, `remove`)

### `sub_agents`

Stores sub-agent metadata and configuration.

Used for:
- startup loading
- CRUD operations from SubAgents UI
- per-agent provider, model, temperature, Top P, and maximum output tokens inside the serialized agent configuration
- autonomous assignment/runtime orchestration

### `sub_agent_configs`

Stores per-agent allowed tool sets and related configuration payloads.

Used for:
- main/sub-agent tool policy loading
- persisted tool enablement

### `workspace_files`

Stores metadata for files imported into the managed workspace.

Used for:
- Files panel display and rename/delete operations
- `workspace:file` tool lookup by ID or relative path
- SHA-256 verification and PDF extracted-text caching
- Workspace search and filesystem/DB reconciliation
- Saved editable canvas documents under `workspace/canvas/`

The table stores relative workspace paths only. External source paths are never persisted.

### `skills`

Stores bounded reusable Markdown authored by the model or user. SQLite FTS5
indexes title and content through database triggers. Each row has an optimistic
revision, a SHA-256 duplicate fingerprint, and model-read telemetry. Reads from
the model increment `read_count` and `last_read_at` atomically; user-panel
views do not. Deletion removes the searchable body and keeps only a minimal
body-free audit tombstone.

### `user_preferences`

Stores user configuration values persisted beyond app restarts.

It now backs the application settings binder through the `PreferenceStore` abstraction.

The server data path is resolved before SQLite opens and is not stored in this table. Runtime
configuration changes are written to `user_preferences`; they do not rewrite a packaged resource
file.

Provider-related entries include:

- `llm.provider.catalog.v1`: versioned provider profiles, models, status, limits, variants, filters, and options
- `llm.provider`
- `ollama.local.url`
- `ollama.model`
- `ollama.api.key`
- `openai.base.url`
- `openai.model`
- `openai.api.key`

Legacy provider entries are migrated into the catalog when no catalog exists. API keys are currently stored as plaintext by product decision. They are excluded from file-based configuration exports and must not be exposed to model context, tool output, or logs.

Canvas documents are stored as regular managed workspace files
under `<server-data-root>/workspace/canvas/` with MIME type
`application/vnd.visual-agent.canvas+xml` (see
`workspace/WorkspaceFilePaths.kt` `CANVAS_MIME_TYPE`). The default
auto-saved document is `current.canvas`; explicit saves use
`canvas.saveDocument(name)` and produce a separate managed file that
shows up in the Files panel and is queryable through `workspace:file`.
The document format is a versioned JSON document (see
`canvas/CanvasDocumentCodec.kt`) and is updated on every mutation by
`InMemoryCanvasService` while the canvas is in use.

## Search/Index Notes

Conversation keyword search is implemented with native SQLite FTS5 queries inside the conversation store and used by the `history` tool.  
When FTS input is invalid, the store falls back to a case-insensitive `LIKE` query.

## Tool History Persistence

Tool calls are persisted as conversation entries (compact text + metadata).  
These entries are restored on restart and rendered in conversation UI as minimized tool events.

## Migration Notes

- The production app no longer creates tables with ad hoc JDBC schema helpers.
- The initial schema is defined in `db/migration/V1__initial_knowledge_schema.sql`.
- Existing local databases keep their data; Flyway adds schema history and applies migrations without resetting content.
- A repository-local legacy `./data/` directory is not auto-migrated because a desktop client may
  be connected to a different server. To keep using that store, stop Visual Agent and pass its
  absolute database path explicitly with `-Dvisual-agent.db.path=/absolute/path/data/visual-agent.db`.
  The workspace is then derived from that database's parent. Do not merge legacy and target roots;
  copy the complete directory only while the application is stopped and only when the target does
  not already exist.

## Operational Notes

If stale WAL/SHM files remain after an unclean shutdown and lock errors persist:

```bash
rm <server-data-root>/visual-agent.db-wal <server-data-root>/visual-agent.db-shm
```

Then restart the application.
