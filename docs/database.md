# Database Schema

## Overview

Visual Agent uses SQLite through Spring Data JPA repositories and Flyway migrations.

Runtime defaults:

- DB path from config (`./data/visual-agent.db`)
- `PRAGMA journal_mode=WAL`
- `PRAGMA busy_timeout=5000`
- Hibernate schema validation in production, migration-driven schema creation through Flyway

The application uses DB-first reads for conversation, todos, and related runtime context.

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

### `user_preferences`

Stores user configuration values persisted beyond app restarts.

It now backs the application settings binder through the `PreferenceStore` abstraction.

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

Canvas-related entries include:

- `canvas.document.xml`: serialized JHotDraw document containing layers, editable figures, image references, and transforms

The canvas document is updated after model changes and restored when the canvas panel is created.

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

## Operational Notes

If stale WAL/SHM files remain after an unclean shutdown and lock errors persist:

```bash
rm data/visual-agent.db-wal data/visual-agent.db-shm
```

Then restart the application.
