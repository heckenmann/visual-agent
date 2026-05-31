# Visual Agent

A Kotlin-based desktop coding agent with JavaFX UI, Spring Boot, Spring AI, and SQLite persistence.

## Current Status

The app is functional end-to-end with persistent chat/todos/settings, Spring AI tool-calling, and streaming UI.

| Feature | UI | Backend | Wired |
|---------|----|---------|-------|
| Chat | Modernized conversation panel, markdown rendering, streaming indicator, tool call timeline | `AgentManager.sendMessage()/streamMessage()` | Yes |
| SubAgents | Card-based panel with create/edit/delete/run/logs | Persistent sub-agent configs and statuses | Yes |
| Todos | Panel + tool integration + count/list/update flows | Persisted in `KnowledgeDb` | Yes |
| Session | Model selector, details, runtime toggles, user instruction | `LLMProvider` + `OllamaClient` | Yes |
| Settings | Theme/font/model/session options | `AppConfig` + DB-backed runtime usage | Yes |
| Tool Calling | Tool events and history rendering in conversation | Spring AI `ToolCallback` path | Yes |
| Knowledge DB | Conversation, todos, tools, agents, preferences, history search | SQLite WAL + indexes | Yes |

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
| Logging | Logback 1.5.x |
| LLM Provider | Spring AI + Ollama |
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
│             TITLE BAR META (connection, model, agents)        │
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
    ├── MainWindow.kt          # FXML-based, panel switching, shortcuts, command palette, window controls
    ├── FxmlLoader.kt          # Type-safe FXML loading utility
    ├── StatusBar.kt           # Legacy status component (not currently used by MainWindow)
    └── panels/
        ├── SessionPanel.kt         # FXML-based, OllamaClient connected, model list + details
        ├── ChatPanel.kt            # Send handler, Enter/Cmd+Ctrl+Enter, loading placeholder, ChatMessage
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

### Completed
- [x] Spring AI migration to current stable starter and tool-calling path
- [x] Persistent conversation/todos/sub-agent/tool history in SQLite
- [x] Tool registry + event bus + conversation tool-call rendering
- [x] Session settings and user instruction persistence
- [x] Streaming response path in conversation UI

### In Progress
- [ ] File-size modularization (target: max 300 LOC per source file)
- [ ] Package-size cleanup and stronger architectural boundaries
- [ ] Additional GUI integration coverage

### Planned
- [ ] Browser/search real backend integration (currently explicit unavailable tool responses)
- [ ] Additional provider support
- [ ] Canvas capability expansion

## License

MIT License
