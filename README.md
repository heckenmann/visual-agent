# Visual Agent

A modern Kotlin-based coding agent with JavaFX UI, utilizing local and cloud LLMs via Ollama.

## Current Status

**Phase 1 — Foundation Complete.** UI shells are built, backend wiring is in progress.

| Feature | UI | Backend | Wired |
|---------|----|---------|-------|
| Chat | Done (send handler + Enter key + custom cells) | `AgentManager.sendMessage()` | Yes — callback wired in MainWindow |
| SubAgents | Done (CSS classes, no inline styles) | Hardcoded in `AgentManager` | Partially — panel creates its own list |
| Todos | Done (Add dialog, Delete, checkbox toggle, badges) | DB table exists | No — panel uses in-memory list |
| Canvas | Done (CSS classes) | Draw methods work | Yes |
| Session | Done (FXML, model selector, details) | OllamaClient connected | Yes — model list + details functional |
| StatusBar | Done (CSS classes) | `checkConnection()` called on startup | Yes — shows connected/disconnected |
| Settings | Done (FXML, theme selector, font spinner) | AppConfig mutable | Yes — theme reload + font size |
| Ollama Client | N/A | `chat()`, `stream()`, `vision()`, `embeddings()`, `getModels()`, `getModelDetails()` | Yes — called by SessionPanel and ChatPanel |
| Knowledge DB | N/A | Tables + partial CRUD, WAL mode, busy_timeout | Partially — initialized but not called by UI |

## Tech Stack

| Component | Version |
|-----------|---------|
| Language | Kotlin 2.1.21 |
| Build System | Gradle 9.4.1 (Kotlin DSL) |
| UI Framework | JavaFX 21.0.2 |
| Database | SQLite 3.45.0.0 (embedded) |
| HTTP Client | Ktor 2.3.7 |
| Serialization | Kotlinx Serialization JSON 1.8.1 |
| Coroutines | Kotlinx Coroutines 1.7.3 |
| Logging | Logback 1.4.14 |
| LLM Provider | Ollama API |
| Linter | ktlint |
| Namespace | `de.heckenmann.visualagent` |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     MAIN WINDOW (JavaFX)                    │
├──────────────┬──────────────────┬───────────────────────────┤
│  SUBAGENTS   │     CHAT         │        TODOS              │
│   Panel      │     Panel        │        Panel              │
├──────────────┴──────────────────┴───────────────────────────┤
│                    CANVAS (visual output)                   │
├─────────────────────────────────────────────────────────────┤
│              STATUS BAR (connection, model, agents)          │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
src/main/kotlin/de/heckenmann/visualagent/
├── Main.kt                    # Application entry point
├── agent/
│   ├── LLMProvider.kt         # Interface: chat, stream, vision, embeddings, getModels, getModelDetails
│   ├── OllamaClient.kt        # Implements LLMProvider, connects to Ollama API
│   ├── AgentManager.kt        # Manages sub-agents, sends messages via OllamaClient
│   ├── SubAgent.kt            # SubAgent data model, AgentStatus enum
│   └── SessionEvent.kt        # Sealed interface for session-level events
├── config/
│   └── AppConfig.kt           # Singleton loaded from app.properties
├── knowledge/
│   └── KnowledgeDb.kt         # SQLite with WAL mode, busy_timeout, table creation + partial CRUD
├── todo/
│   └── Todo.kt                # Todo, Priority, Status models
└── ui/
    ├── MainWindow.kt          # FXML-based, panel switching, window controls, wires backend to UI
    ├── FxmlLoader.kt          # Type-safe FXML loading utility
    ├── StatusBar.kt           # Connection status with CSS classes
    └── panels/
        ├── SessionPanel.kt         # FXML-based, OllamaClient connected, model list + details
        ├── ChatPanel.kt            # Send handler, Enter key, setOnSendMessage callback, ChatMessage
        ├── TodoPanel.kt            # FXML-based, Add dialog, Delete, checkbox toggle, priority badges
        ├── SubAgentsPanel.kt       # Agent list built in code, CSS classes (no inline styles)
        ├── CanvasPanel.kt          # Drawing canvas built in code, CSS classes
        └── ApplicationSettingsPanel.kt  # FXML-based, theme selector, font size spinner
```

## Prerequisites

- Java 21+
- Gradle 9.4.1
- Ollama running (`ollama serve`)

## Quick Start

```bash
# Build
gradle build

# Run
gradle run

# Copy dependencies to lib/
gradle copyAllDependencies
```

## Roadmap

### Phase 1: Foundation (Current)
- [x] Gradle Project Setup
- [x] JavaFX MainWindow with CSS styling
- [x] Ollama Local Client (REST API)
- [x] UI Panels (Chat, SubAgents, Todos, Canvas, StatusBar, Session, Settings)
- [x] Wire Chat to AgentManager (send handler + callback)
- [x] Wire SessionPanel to OllamaClient (model list + details)
- [x] Wire StatusBar to OllamaClient (checkConnection on startup)
- [x] Todo Add/Delete/Complete with dialog and ListCell
- [x] Application Settings with theme selector and font size
- [ ] Remove hardcoded sample data from AgentManager and SubAgentsPanel
- [ ] Wire TodoPanel to KnowledgeDb for persistence

### Phase 2: Core Features
- [ ] SubAgent System loaded from DB
- [ ] Todo Manager with SQLite persistence
- [ ] Knowledge DB full CRUD
- [ ] Chat with streaming responses
- [ ] Personalization (Name, Image storage)

### Phase 3: Advanced Features
- [ ] Canvas with LLM-driven visual output
- [ ] Ollama Cloud Provider
- [ ] Tool-Calling Interface

### Phase 4: Integration
- [ ] Firefox Browser Controller
- [ ] Screen Capture + Analysis
- [ ] Input Simulation (Robot)
- [ ] Multi-Provider Support

## License

MIT License