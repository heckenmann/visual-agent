# Database Schema

## Overview

Visual Agent uses SQLite through `KnowledgeDb`.

Runtime defaults:

- DB path from config (`./data/visual-agent.db`)
- `PRAGMA journal_mode=WAL`
- `PRAGMA busy_timeout=5000`

The application uses DB-first reads for conversation, todos, and related runtime context.

## Active Tables

### `conversation_history`

Stores persisted chat timeline, including assistant/tool-related messages.

Used for:
- initial history load (limited page)
- incremental "load older" behavior
- keyword search via history tool

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
- autonomous assignment/runtime orchestration

### `sub_agent_configs`

Stores per-agent allowed tool sets and related configuration payloads.

Used for:
- main/sub-agent tool policy loading
- persisted tool enablement

### `user_preferences`

Stores user configuration values persisted beyond app restarts.

## Search/Index Notes

Conversation keyword search is implemented in `KnowledgeDb` and used by the `history` tool.  
Indices exist to support session/time-based retrieval and query paths used by history pagination/search.

## Tool History Persistence

Tool calls are persisted as conversation entries (compact text + metadata).  
These entries are restored on restart and rendered in conversation UI as minimized tool events.

## Operational Notes

If stale WAL/SHM files remain after an unclean shutdown and lock errors persist:

```bash
rm data/visual-agent.db-wal data/visual-agent.db-shm
```

Then restart the application.
