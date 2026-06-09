# Visual Agent

Visual Agent is a Kotlin desktop coding agent with JavaFX UI, Spring Boot/Spring AI, and Spring Data JPA on SQLite.

## Current Status

The app is functional end-to-end with persistent chat/todos/settings, Spring AI tool-calling, streaming responses, and persisted tool-call history in conversation.

| Feature | UI | Backend | Wired |
|---------|----|---------|-------|
| Chat | Modernized conversation panel, markdown rendering, streaming indicator, tool call timeline | `AgentManager.sendMessage()/streamMessage()` | Yes |
| SubAgents | Card-based panel with create/edit/delete/run/logs | Persistent sub-agent configs and statuses | Yes |
| Todos | Panel + tool integration + count/list/update flows | Persisted via Spring Data JPA stores | Yes |
| Session | Provider selector, model selector, OpenAI key/base URL, runtime toggles, user instruction | `ConfiguredLLMProvider` + Ollama/OpenAI clients | Yes |
| Settings | Theme/font/model/session options | `AppConfig` + DB-backed runtime usage | Yes |
| Tool Calling | Tool events and minimized tool entries in conversation | Spring AI `ToolCallback` path | Yes |
| Persistence | Conversation, todos, tools, agents, preferences, history search | SQLite + JPA + Flyway + FTS5 | Yes |

## Tech Stack

| Component | Version |
|-----------|---------|
| Language | Kotlin 2.1.21 |
| Build System | Gradle 9.4.1 (Kotlin DSL) |
| UI Framework | JavaFX 21.0.2 |
| Database | SQLite 3.45.0.0 (embedded, via JPA/Flyway) |
| HTTP Client | Ktor 2.3.7 |
| Serialization | Kotlinx Serialization JSON 1.8.1 |
| Coroutines | Kotlinx Coroutines 1.7.3 |
| Logging | Logback 1.5.x |
| LLM Provider | Spring AI + Ollama/OpenAI (`ChatModel`) |
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
│      TITLE BAR META (connection, model, active agents)      │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
src/main/kotlin/de/heckenmann/visualagent/
├── Main.kt                    # Application entry point
├── agent/
│   ├── LLMProvider.kt         # Interface: chat, stream, vision, embeddings, getModels, getModelDetails
│   ├── ConfiguredLLMProvider.kt # Primary provider router selected by AppConfig
│   ├── OllamaClient.kt        # Implements LLMProvider via Spring AI + Ollama ChatModel
│   ├── OllamaPromptFactory.kt # Builds request-scoped prompts/options/tool callbacks
│   ├── OllamaToolRecovery.kt  # Unknown tool-name recovery path
│   ├── openai/                # OpenAI-compatible provider implementation
│   ├── AgentManager.kt        # Main orchestration: chat, todos, history, sub-agents
│   ├── SubAgent.kt            # SubAgent data model, AgentStatus enum
│   └── SessionEvent.kt        # Sealed interface for session-level events
│   └── tools/                 # Core/File/Runtime tools + registry/event bus
├── config/
│   └── AppConfig.kt           # Singleton loaded from app.properties
│   └── AppConfigPersistenceBinder.kt # DB-backed settings persistence binding
├── knowledge/
│   ├── PersistenceEntities.kt  # JPA entity classes for SQLite tables
│   ├── PersistenceRepositories.kt # Spring Data repositories + custom FTS query path
│   ├── PersistenceStores.kt    # Typed service-layer stores and domain records
│   └── PersistenceConverters.kt # Instant/boolean SQLite converters
├── todo/
│   ├── Todo.kt                # Todo, Priority, Status models
│   └── TodoConfiguration.kt   # Spring bean wiring for TodoManager
└── ui/
    ├── MainWindow.kt          # FXML-based, panel switching, shortcuts, command palette, window controls
    ├── MainWindow*Wiring.kt   # Chat/sub-agent/tool event wiring modules
    ├── FxmlLoader.kt          # Type-safe FXML loading utility
    ├── StatusBar.kt           # Legacy status component (not currently used by MainWindow)
    └── panels/
        ├── SessionPanel.kt         # FXML-based, model/session settings panel
        ├── ChatPanel.kt            # Conversation panel shell
        ├── ChatMessage*.kt         # Message rendering/list/runtime status controllers
        ├── TodoPanel.kt            # FXML-based, Add dialog, Delete, checkbox toggle, priority badges
        ├── SubAgentsPanel.kt       # Agent list built in code, CSS classes (no inline styles)
        ├── CanvasPanel.kt          # Drawing canvas built in code, CSS classes
        └── ApplicationSettingsPanel.kt  # FXML-based, theme selector, font size spinner
```

## Prerequisites

- Java 21+
- Gradle 9.4.1
- Ollama running (`ollama serve`) for local models, or an OpenAI/OpenAI-compatible API key for cloud models

## Quick Start

```bash
# Build
gradle build

# Run
gradle run

# Copy dependencies to lib/
gradle copyAllDependencies
```

## Provider Setup

Visual Agent supports two provider modes:

- `ollama`: local Ollama models from `ollama.local.url`
- `openai`: OpenAI or OpenAI-compatible `/v1` chat endpoints

The Session panel lets the user choose the provider, refresh the provider-specific model list, and save the active model. For OpenAI-compatible usage, configure:

- OpenAI API Key
- OpenAI Base URL, default `https://api.openai.com`
- OpenAI model, default `gpt-4o-mini`

Current product decision: the OpenAI API key is stored as plaintext in the SQLite `user_preferences` table.

## Persistence

The app now uses a Spring Data JPA persistence layer backed by SQLite and Flyway migrations.

- Runtime schema is managed by `Flyway` with versioned SQL migrations.
- Hibernate validates the mapped entities; it does not create or mutate tables in production.
- The existing SQLite data file remains the source of truth for local desktop usage.
- Conversation search still uses SQLite FTS5 with a fallback `LIKE` query for invalid FTS input.
- The old JDBC `KnowledgeDb` facade has been replaced by typed stores such as `ConversationStore`, `TodoStore`, `SubAgentStore`, and `PreferenceStore`.

## Quality Gates

Run before commit:

```bash
./gradlew ktlintCheck check test
```

Notes:

- `ktlintJavadocCheck`: enforces KDoc/Javadoc for explicit public declarations.
- `unusedCodeCheck`: flags removable unused private declarations.
- `locAndPackageSizeCheck`: currently warning-only while modularization is in progress.

## Model Context (Main Agent)

Each main-agent request includes:

- system context prompt with authoritative todo summary + current todo list
- active provider and active model from DB-backed settings
- optional user session instruction (`userModelInstruction`)
- last 20 persisted conversation messages from DB
- enabled tool IDs from `AgentToolConfigService`
- request metadata (`sessionId`, `agent`, optional `requestId`)
- strict tool-name guard message (exact callable function names)

## Roadmap

### Completed
- [x] Spring AI migration to current stable starter and tool-calling path
- [x] Persistent conversation/todos/sub-agent/tool history in SQLite
- [x] Tool registry + event bus + conversation tool-call rendering
- [x] Session settings and user instruction persistence
- [x] Streaming response path in conversation UI
- [x] OpenAI/OpenAI-compatible provider support with provider-specific model selection

### In Progress
- [ ] File-size modularization (target: max 300 LOC per source file)
- [ ] Package-size cleanup and stronger architectural boundaries
- [ ] Additional GUI integration coverage

### Planned
- [ ] Browser/search real backend integration (currently explicit unavailable tool responses)
- [ ] Canvas capability expansion

## License

MIT License
