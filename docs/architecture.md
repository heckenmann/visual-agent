# Architecture

## Overview

Visual Agent follows a modular architecture with clear separation of concerns. UI shells are built and key backend wiring is in place — ChatPanel sends messages via AgentManager, SessionPanel fetches models from OllamaClient, and StatusBar reflects connection status with quick reconnect actions.

## Layer Model

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│                    (JavaFX UI Components)                   │
├─────────────────────────────────────────────────────────────┤
│                    Application Layer                        │
│              (Agent Logic, State Management)                │
├─────────────────────────────────────────────────────────────┤
│                    Domain Layer                             │
│         (Business Logic, Domain Models, Interfaces)         │
├─────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                     │
│    (Ollama Client, SQLite Repository, Browser Controller)   │
└─────────────────────────────────────────────────────────────┘
```

## Current Implementation Status

| Component | UI | Backend | Wired |
|-----------|----|---------|-------|
| MainWindow | Done (FXML, window controls) | N/A | N/A |
| ChatPanel | Done (send handler, Enter/Cmd+Ctrl+Enter, loading state, custom cells) | `AgentManager.sendMessage()` | **Wired** — callback in MainWindow |
| SubAgentsPanel | Done (CSS classes) | `AgentManager` has hardcoded agents | **Partially** — panel creates its own list |
| TodoPanel | Done (Add dialog, Delete, checkbox, badges) | `KnowledgeDb` has `todos` table | **Not wired** — panel uses in-memory list |
| CanvasPanel | Done (CSS classes) | Draw methods exist | Functional |
| SessionPanel | Done (FXML, model selector, details) | `OllamaClient.getModels()`, `getModelDetails()` | **Wired** — model list + details functional |
| StatusBar | Done (CSS classes + Retry/Reconnect actions) | `checkConnection()` called on startup | **Wired** — shows connected/disconnected, actions trigger reconnect |
| ApplicationSettingsPanel | Done (FXML, theme, font size) | AppConfig mutable properties | **Wired** — theme reload + font size |
| OllamaClient | N/A | `chat()`, `stream()`, `vision()`, `embeddings()`, `getModels()`, `getModelDetails()` | **Wired** — called by SessionPanel and ChatPanel; chat/stream use Spring AI tools |
| KnowledgeDb | N/A | `saveMemory()`, `searchMemories()`, preferences CRUD, WAL mode, busy_timeout | Initialized but not called by UI |

## Main Components

### 1. UI Layer (JavaFX)

| Component | Description | Status |
|-----------|-------------|--------|
| `MainWindow` | FXML-based with BorderPane layout, panel switching, keyboard shortcuts (`Cmd/Ctrl+1..6`) and command palette (`Cmd/Ctrl+K`) | Working |
| `ChatPanel` | Chat with ListView + custom cells + send handler + Enter/Cmd+Ctrl+Enter + assistant loading state | Working — wired to AgentManager |
| `SubAgentsPanel` | Live view of SubAgents with status indicators | Working — creates own default agents |
| `TodoPanel` | Todo list with Add dialog, Delete, checkbox toggle, priority badges | Working — in-memory only |
| `CanvasPanel` | Drawing canvas with text, rect, line, circle | Working |
| `SessionPanel` | Model selector, context slider, streaming toggle, model details | Working — wired to OllamaClient |
| `StatusBar` | Connection status, model name, agent count, Retry/Reconnect | Working — updated on startup |
| `ApplicationSettingsPanel` | Theme selector, font size spinner | Working — applies changes live |
| `FxmlLoader` | Type-safe FXML loading utility | Working |

### 2. Agent Layer

**`AgentManager`** — wired to ChatPanel via `setOnSendMessage` callback. Still uses hardcoded agents.

```kotlin
// Current (hardcoded):
private fun loadAgentsFromDb() {
    subAgents["1"] = SubAgent("1", "Researcher", "Web research", AgentStatus.IDLE)
    subAgents["2"] = SubAgent("2", "Coder", "Code implementation", AgentStatus.IDLE)
    subAgents["3"] = SubAgent("3", "Documenter", "Documentation", AgentStatus.IDLE)
}

// Needed: query KnowledgeDb.sub_agents table
```

### 3. Provider Layer

**`OllamaClient`** — implements `LLMProvider`, called by SessionPanel and ChatPanel.

| Method | Status | Notes |
|--------|--------|-------|
| `chat()` | Implemented | Uses Spring AI `ChatModel` with request-scoped function callbacks |
| `stream()` | Implemented | Uses Spring AI streaming with request-scoped function callbacks |
| `vision()` | Implemented | **Bug**: hardcodes model `"llava"` instead of config |
| `embeddings()` | Implemented | **Bug**: uses raw string interpolation, no escaping of `$text` |
| `checkConnection()` | Implemented | Called on startup by MainWindow |
| `getModels()` | Implemented | Called by SessionPanel via `setOllamaClient()` |
| `getModelDetails()` | Implemented | Called by SessionPanel on model selection |

### 4. Tool Layer

`ToolRegistry` exposes application tools to Spring AI as `FunctionCallback` instances. Agent code only passes provider-neutral `ToolId` values through `ChatRequestContext`; provider-specific callback creation stays inside the LLM provider boundary.

Implemented tool IDs: `ui`, `file:read`, `file:list`, `file:glob`, `file:grep`, `file:write`, `file:edit`, `terminal`, `browser`, `search`, `context`, `pwd`.

### 5. Data Layer

**`KnowledgeDb`** — creates all tables on `init()`, has partial CRUD. WAL mode and `busy_timeout=5000` enabled.

| Table | Read | Write | Status |
|-------|------|-------|--------|
| `long_term_memory` | `searchMemories()` | `saveMemory()` | Working but never called by UI |
| `user_preferences` | `getPreference()` | `setPreference()` | Working but never called by UI |
| `conversation_history` | **Missing** | **Missing** | Table created, no CRUD methods |
| `todos` | **Missing** | **Missing** | Table created, no CRUD methods |
| `sub_agents` | **Missing** | **Missing** | Table created, no CRUD methods |
| `project_knowledge` | **Missing** | **Missing** | Table created, no methods |

## Known Issues

1. **SubAgentsPanel creates its own agent list** — not populated from AgentManager, leads to duplicate data
2. **TodoPanel uses in-memory list** — not persisted to KnowledgeDb
3. **Vision model hardcoded to `"llava"`** — should be configurable
4. **Embeddings uses raw string interpolation** — no JSON escaping, injection risk
5. **AgentManager.loadAgentsFromDb() hardcoded** — should query KnowledgeDb.sub_agents table

## Thread Model

- **JavaFX Application Thread**: UI updates
- **Coroutines Dispatcher**: Async operations (AgentManager, SessionPanel)
- **IO Dispatcher**: Database and network (implicit via Ktor CIO)
- **Default Dispatcher**: CPU-intensive tasks

## Configuration Files

| File | Purpose |
|------|---------|
| `src/main/resources/config/app.properties` | General settings |
| `src/main/resources/fxml/*.fxml` | FXML layouts for panels |
| `src/main/resources/styles/dark.css` | Dark theme stylesheet (30+ selectors) |

## Extensibility

The system is designed following the Open-Closed Principle:

- New providers by implementing `LLMProvider`
- New tools by registering in `ToolRegistry`
- New UI components by extending JavaFX `Region`
