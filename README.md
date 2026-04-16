# Visual Agent

A modern Kotlin-based coding agent with JavaFX UI, utilizing local and cloud LLMs via Ollama.

## Current Status

**Phase 1 — Early Implementation.** UI shells are built, backend wiring is in progress.

| Feature | UI | Backend | Wired |
|---------|----|---------|-------|
| Chat | Done | `AgentManager.sendMessage()` exists | No — send button has no handler |
| SubAgents | Done | Hardcoded in `AgentManager` | No — panel creates its own data |
| Todos | Done | DB table exists | No — panel uses sample data |
| Canvas | Done | Draw methods work | Yes |
| Status Bar | Done | Update methods exist | No — always shows "Disconnected" |
| Ollama Client | N/A | Implemented, never called | No |
| Knowledge DB | N/A | Tables + partial CRUD | Partially |

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Build System | Gradle (Kotlin DSL) |
| UI Framework | JavaFX 21 |
| Database | SQLite (embedded) |
| HTTP Client | Ktor |
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
├── agent/                     # LLM client, provider interface, SubAgent model
├── config/                    # AppConfig singleton
├── knowledge/                 # SQLite KnowledgeDb
├── todo/                      # Todo model
└── ui/                        # JavaFX UI panels
    ├── MainWindow.kt
    ├── StatusBar.kt
    └── panels/                 # SubAgents, Chat, Todo, Canvas panels
```

## Prerequisites

- Java 21+
- Gradle 8.x+
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
- [x] UI Panels (Chat, SubAgents, Todos, Canvas, StatusBar)
- [ ] Wire UI to backend (send button, connection check, agent status)
- [ ] Remove hardcoded sample data

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