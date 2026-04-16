# Architecture

## Overview

Visual Agent follows a modular architecture with clear separation of concerns. The project is in early implementation stage — UI shells exist but backend wiring is incomplete.

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
| MainWindow | Done | N/A | N/A |
| ChatPanel | Done (no send handler) | `AgentManager.sendMessage()` exists | **Not wired** |
| SubAgentsPanel | Done | `AgentManager` has hardcoded agents | **Not wired** — panel creates its own duplicate list |
| TodoPanel | Done | `KnowledgeDb` has `todos` table | **Not wired** — panel uses hardcoded sample data |
| CanvasPanel | Done | Draw methods exist | Partially functional |
| StatusBar | Done | `updateConnectionStatus()`, `updateModel()` exist | **Not wired** — always shows "Disconnected" |
| OllamaClient | N/A | `chat()`, `stream()`, `vision()`, `embeddings()` | **Exists but never called** |
| KnowledgeDb | N/A | `saveMemory()`, `searchMemories()`, preferences CRUD | **Exists but never initialized** |

## Main Components

### 1. UI Layer (JavaFX)

| Component | Description | Status |
|-----------|-------------|--------|
| `MainWindow` | Main window with BorderPane layout | Working |
| `ChatPanel` | Chat interface with ListView + TextField + Button | Missing: send button event handler |
| `SubAgentsPanel` | Live view of SubAgents with status indicators | Missing: populated from AgentManager |
| `TodoPanel` | Todo list with CheckBox + priority colors | Missing: loaded from DB, add button |
| `CanvasPanel` | Drawing canvas with text, rect, line, circle | Working |
| `StatusBar` | Connection status, model name, agent count | Missing: never updated from backend |

### 2. Agent Layer

**`AgentManager`** — exists but uses hardcoded agents instead of DB.

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

**`OllamaClient`** — implements `LLMProvider`, fully coded but never instantiated by the UI.

| Method | Status | Notes |
|--------|--------|-------|
| `chat()` | Implemented | Uses `ChatRequest.serializer()` |
| `stream()` | Implemented | Parses NDJSON response line by line |
| `vision()` | Implemented | **Bug**: hardcodes model `"llava"` instead of config |
| `embeddings()` | Implemented | **Bug**: uses raw string interpolation, no escaping of `$text` |
| `checkConnection()` | Implemented | Never called — `isConnected()` always returns `false` |

### 4. Data Layer

**`KnowledgeDb`** — creates all tables on `init()`, has partial CRUD.

| Table | Read | Write | Status |
|-------|------|-------|--------|
| `long_term_memory` | `searchMemories()` | `saveMemory()` | Working but never called |
| `user_preferences` | `getPreference()` | `setPreference()` | Working but never called |
| `conversation_history` | **Missing** | **Missing** | Table created, no CRUD methods |
| `todos` | **Missing** | **Missing** | Table created, no CRUD methods |
| `sub_agents` | **Missing** | **Missing** | Table created, no CRUD methods |
| `project_knowledge` | **Missing** | **Missing** | Table created, no CRUD methods |
| `tool_executions` | **Missing** | **Missing** | Table created, no CRUD methods |

## Known Issues

1. **No wiring between UI and backend** — MainWindow creates panels independently without passing AgentManager, OllamaClient, or KnowledgeDb
2. **ChatPanel send button has no event handler** — clicking "Send" does nothing
3. **StatusBar never updated** — methods exist but are never called
4. **OllamaClient.checkConnection() never called** — app always shows "Disconnected"
5. **Vision model hardcoded to `"llava"`** — should be configurable
6. **Embeddings uses raw string interpolation** — no JSON escaping, injection risk

## Thread Model

- **JavaFX Application Thread**: UI updates
- **Coroutines Dispatcher**: Async operations
- **IO Dispatcher**: Database and network
- **Default Dispatcher**: CPU-intensive tasks

## Configuration Files

| File | Purpose |
|------|---------|
| `src/main/resources/config/app.properties` | General settings (only file that exists) |

## Extensibility

The system is designed following the Open-Closed Principle:

- New providers by implementing `LLMProvider`
- New tools by registering in `ToolRegistry`
- New UI components by extending JavaFX `Region`