# Database Schema

## Overview

Visual Agent uses SQLite as an embedded database via `KnowledgeDb` at `de.heckenmann.visualagent.knowledge.KnowledgeDb`.

The database is initialized with WAL mode and a busy timeout for better concurrent read/write performance and resilience against lock contention.

## Connection Configuration

- Default path: `./data/visual-agent.db` (from `app.properties`)
- Directory is auto-created on init via `ensureDataDirectory()`
- WAL mode is enabled: `PRAGMA journal_mode=WAL`
- Busy timeout is set: `PRAGMA busy_timeout=5000` — SQLite will wait up to 5 seconds for a locked database before returning `SQLITE_BUSY`
- If stale WAL/SHM files from a crashed process cause `SQLITE_BUSY`, delete `data/visual-agent.db-wal` and `data/visual-agent.db-shm` before restarting

## Schema

All tables are created in `KnowledgeDb.initDatabase()`. The actual SQL matches what's below.

### long_term_memory

```sql
CREATE TABLE IF NOT EXISTS long_term_memory (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    embedding BLOB,
    tags TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    access_count INTEGER DEFAULT 0,
    last_accessed TIMESTAMP
);
```

**CRUD Status:**
| Operation | Method | Status |
|-----------|--------|--------|
| Create | `saveMemory(content, tags)` | Implemented |
| Read | `searchMemories(query, limit)` | Implemented (text search only, no embedding) |
| Update | — | Missing |
| Delete | — | Missing |

### conversation_history

```sql
CREATE TABLE IF NOT EXISTS conversation_history (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**CRUD Status:**
| Operation | Method | Status |
|-----------|--------|--------|
| Create | — | **Missing** |
| Read | — | **Missing** |
| Update | — | Missing |
| Delete | — | Missing |

### todos

```sql
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
```

**CRUD Status:**
| Operation | Method | Status |
|-----------|--------|--------|
| Create | — | **Missing** |
| Read | — | **Missing** |
| Update | — | **Missing** |
| Delete | — | Missing |

### sub_agents

```sql
CREATE TABLE IF NOT EXISTS sub_agents (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT DEFAULT 'IDLE',
    current_task TEXT,
    parent_agent_id TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**CRUD Status:**
| Operation | Method | Status |
|-----------|--------|--------|
| Create | — | **Missing** |
| Read | — | **Missing** |
| Update | — | **Missing** |
| Delete | — | Missing |

### user_preferences

```sql
CREATE TABLE IF NOT EXISTS user_preferences (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    type TEXT DEFAULT 'string',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**CRUD Status:**
| Operation | Method | Status |
|-----------|--------|--------|
| Create/Update | `setPreference(key, value)` | Implemented |
| Read | `getPreference(key)` | Implemented |
| Delete | — | Missing |

### project_knowledge

```sql
CREATE TABLE IF NOT EXISTS project_knowledge (
    id TEXT PRIMARY KEY,
    project_path TEXT NOT NULL UNIQUE,
    name TEXT,
    description TEXT,
    summary TEXT,
    last_accessed TIMESTAMP
);
```

**CRUD Status:**
| Operation | Method | Status |
|-----------|--------|--------|
| All | — | **Missing** (table created, no methods) |

## Database Configuration

- Default path: `./data/visual-agent.db` (from `app.properties`)
- Directory is auto-created on init via `ensureDataDirectory()`
- WAL mode is enabled: `PRAGMA journal_mode=WAL`
- Busy timeout: `PRAGMA busy_timeout=5000`

## Priority: Missing CRUD Methods

The following methods need to be added to `KnowledgeDb` to support the UI:

1. **`saveConversation(sessionId, role, content)`** — for chat persistence
2. **`getConversations(sessionId)`** — for loading chat history
3. **`saveTodo(todo: Todo)`** — for todo persistence
4. **`getTodos()`** — for loading todos
5. **`updateTodoStatus(id, status)`** — for todo status changes
6. **`deleteTodo(id)`** — for todo deletion
7. **`saveSubAgent(agent: SubAgent)`** — for agent persistence
8. **`getSubAgents()`** — for loading agents from DB
9. **`updateSubAgentStatus(id, status, task)`** — for agent status changes