# Visual Agent

<p align="center">
  <img src="src/main/resources/icons/visual-agent.svg" alt="Visual Agent icon" width="160">
</p>

Visual Agent is a Kotlin desktop coding agent with JavaFX UI, Spring Boot/Spring AI, and Spring Data JPA on SQLite.

> **Development notice:** This project was written entirely with LLM assistance and is still under active development.
> Expect rapid changes, incomplete features, and rough edges until the project reaches a stable release.

## Current Status

The app is functional end-to-end with persistent chat/todos/settings, Spring AI tool-calling, streaming responses, persisted tool-call history, managed workspace files, and draggable internal workspace windows.

| Feature | UI | Backend | Wired |
|---------|----|---------|-------|
| Chat | Modernized conversation panel, markdown rendering, streaming indicator, tool call timeline | `AgentManager.sendMessage()/streamMessage()` | Yes |
| SubAgents | Card-based panel with create/edit/delete/run/logs and per-agent provider/model parameters | Persistent sub-agent configs and statuses | Yes |
| Todos | Panel + tool integration + count/list/update flows | Persisted via Spring Data JPA stores | Yes |
| Session | Dynamic provider profiles, filtered model catalogs, credentials/base URLs, runtime toggles, user instruction | `ProviderCatalogService` + runtime adapters | Yes |
| Settings | Theme/font/model/session options | `AppConfig` + DB-backed runtime usage | Yes |
| Files | Managed workspace import/search/rename/delete/sync, image/canvas handoff | `WorkspaceFileService` + `WorkspaceFileTool` | Yes |
| Canvas | Persistent editable shapes, freehand paths, image import, fixed surface size, zoom/grid/export, model-readable snapshots | JHotDraw 8 drawing model, undo manager, JavaFX-safe canvas service | Yes |
| Internal Windows | Draggable/resizable panels, persisted layout, model-facing arrangement tool | `WorkspaceWindowManager` + `WorkspaceLayoutTool` | Yes |
| Tool Calling | Tool events, minimized tool entries, per-agent tool toggles, sub-agent canvas/workspace/use-case tools | Spring AI `ToolCallback` path | Yes |
| Use Cases | Packaged product use-case catalog available to agents | `docs/usecases/` + `UseCaseTool` | Yes |
| Persistence | Conversation, todos, tools, agents, preferences, history search, workspace metadata | SQLite + JPA + Flyway + FTS5 | Yes |

## Tech Stack

| Component | Version |
|-----------|---------|
| Language | Kotlin 2.2.21 |
| Build System | Gradle 9.4.1 (Kotlin DSL) |
| Application Framework | Spring Boot 4.1.0 |
| AI Integration | Spring AI 2.0.0 |
| UI Framework | JavaFX 21.0.2 |
| Drawing Framework | JHotDraw 8 version 0.5 |
| Database | SQLite 3.45.0.0 (embedded, via JPA/Flyway) |
| HTTP Client | Spring `RestClient` / `WebClient` |
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
├──────────────┬──────────────────────────────────────────────┤
│ LEFT RAIL    │ INTERNAL WORKSPACE DESKTOP                   │
│ navigation   │ draggable/resizable windows                  │
│              │ chat, session, agents, todos, canvas, files  │
│              │ settings                                     │
├──────────────┴──────────────────────────────────────────────┤
│ STATUS FOOTER (workspace persistence, agent activity)       │
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
│   ├── ollama/                # Authenticated Ollama API client configuration
│   ├── openai/                # OpenAI-compatible provider implementation
│   ├── provider/              # Provider catalog, model metadata, runtime settings
│   ├── context/               # Request context and prompt payload helpers
│   ├── conversation/          # Conversation history and message rendering models
│   ├── AgentManager.kt        # Main orchestration: chat, todos, history, sub-agents
│   ├── SubAgent.kt            # SubAgent data model, AgentStatus enum
│   └── SessionEvent.kt        # Sealed interface for session-level events
│   └── tools/                 # Core/file/runtime/canvas/workspace/use-case tools + registry/event bus
├── config/
│   └── AppConfig.kt           # Singleton loaded from DB preferences after DB-path bootstrap
│   └── AppConfigPersistenceBinder.kt # DB-backed settings persistence binding
├── knowledge/
│   ├── PersistenceEntities.kt  # JPA entity classes for SQLite tables
│   ├── PersistenceRepositories.kt # Spring Data repositories + custom FTS query path
│   ├── PersistenceStores.kt    # Typed service-layer stores and domain records
│   └── PersistenceConverters.kt # Instant/boolean SQLite converters
├── todo/
│   ├── Todo.kt                # Todo, Priority, Status models
│   └── TodoConfiguration.kt   # Spring bean wiring for TodoManager
├── workspace/
│   ├── WorkspaceFileModels.kt  # Workspace file records and search/sync models
│   ├── WorkspaceFilePaths.kt   # Managed workspace root/path handling
│   └── WorkspaceFileService.kt # Import/search/hash/read/PDF/image operations
└── ui/
    ├── MainWindow.kt          # FXML-based shell, shortcuts, command palette, workspace windows
    ├── MainWindow*Wiring.kt   # Chat/sub-agent/tool event wiring modules
    ├── InternalWorkspaceWindow.kt # Draggable/resizable internal window container
    ├── WorkspaceWindowManager.kt  # Registration, focus, bounds, and layout snapshots
    ├── WorkspaceLayout*.kt        # Layout persistence and model-facing layout service
    ├── FxmlLoader.kt          # Type-safe FXML loading utility
    ├── StatusBar.kt           # Footer component for persistence/activity/reconnect status
    └── panels/
        ├── SessionPanel.kt         # FXML-based, model/session settings panel
        ├── ChatPanel.kt            # Conversation panel shell
        ├── ChatMessage*.kt         # Message rendering/list/runtime status controllers
        ├── TodoPanel.kt            # FXML-based, Add dialog, Delete, checkbox toggle, priority badges
        ├── SubAgentsPanel.kt       # Agent list built in code, CSS classes (no inline styles)
        ├── canvas/                 # JHotDraw editor panel, toolbar, and model-facing canvas operations
        ├── FilesPanel.kt           # Managed workspace file import/search/edit/sync panel
        └── ApplicationSettingsPanel.kt  # FXML-based, theme selector, font size spinner
```

## Prerequisites

- Java 21+
- Gradle 9.4.1
- Ollama running (`ollama serve`) or a reachable Ollama endpoint, or an OpenAI/OpenAI-compatible API key

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

Visual Agent supports persisted provider profiles backed by two runtime adapters:

- `OLLAMA`: local or secured Ollama-compatible endpoints
- `OPENAI_COMPATIBLE`: OpenAI and compatible `/v1` endpoints

The Session panel lets the user add, edit, delete, and select provider profiles, refresh provider-specific models, and configure model status, limits, filters, and options. Each sub-agent may inherit the session selection or choose its own provider, model, variant, generation parameters, and provider-specific options.

For Ollama usage, configure:

- Ollama Base URL, default `http://localhost:11434`
- Optional Ollama API Key for endpoints protected by bearer authentication
- Ollama model

When an Ollama profile has a non-blank API key, every synchronous and streaming request includes `Authorization: Bearer <key>`. Profile URL and key changes apply to subsequent requests without restarting the application.

For OpenAI-compatible usage, configure:

- OpenAI API Key
- OpenAI Base URL, default `https://api.openai.com`
- OpenAI model, default `gpt-4o-mini`

Current product decision: provider API keys are stored as plaintext in the SQLite `user_preferences` table. They are excluded from configuration exports and must never be exposed to model context, tool output, or logs.

## Configuration Storage

`src/main/resources/config/app.properties` is bootstrap-only and should contain only the database connection path, currently:

```properties
database.path=./data/visual-agent.db
```

After the database is available, runtime configuration is stored in SQLite `user_preferences`. Normal application saves do not rewrite `app.properties`.

Configuration exports remain available for deliberate user-initiated import/export flows. Exports contain non-secret runtime settings but exclude provider API keys.

## Workspace Files

Imported user files are copied into a managed workspace directory next to the configured SQLite database directory. With the default database path, the workspace root is:

```text
./data/workspace/
```

The Files panel supports:

- import through a JavaFX file chooser
- list, inspect, rename, delete, refresh, search, and filesystem/DB sync
- copy managed relative paths and SHA-256 hashes
- open supported images and editable canvas documents in the Canvas panel

External source paths are not persisted. Workspace metadata is stored in SQLite, while file bytes remain under the managed workspace folder. Generated files, such as rendered PDF pages, are written under the managed workspace and recorded with metadata and hashes.

## Canvas

The Canvas panel uses JHotDraw 8 for editable figures and persists the current drawing document. It supports:

- fixed user-configurable canvas surface size independent of window size
- freehand pen, selectable/editable figures, image import, delete selection, undo/redo
- zoom, grid, PNG export, and immutable model-readable PNG/JPG captures
- save/open of editable canvas documents through the managed workspace
- model-facing canvas tool calls for reading and modifying the canvas

The internal Canvas window has content-aware minimum sizing and scrollable content so the toolbar and drawing viewport stay inside the window frame.

## Internal Workspace Windows

Primary panels are hosted as draggable and resizable internal windows on the workspace desktop. Window position, size, visibility, and z-order are persisted and restored on restart. Restored windows are clamped into the visible desktop and respect content minimum sizes.

Agents can query and arrange the internal windows through the workspace layout tool when that tool is enabled.

## Persistence

The app now uses a Spring Data JPA persistence layer backed by SQLite and Flyway migrations.

- Runtime schema is managed by `Flyway` with versioned SQL migrations.
- Hibernate validates the mapped entities; it does not create or mutate tables in production.
- The existing SQLite data file remains the source of truth for local desktop usage.
- Managed workspace file metadata is stored in SQLite; file bytes are stored under `./data/workspace/` by default.
- Internal workspace window layout is persisted in user preferences.
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
- `useCaseDocumentationCheck`: verifies maintained use-case documentation is packaged for the agent.

## Tool System

Tool availability is configured per main agent and per sub-agent. The main request context receives only enabled tool IDs; sub-agents can receive specialized tools according to their configuration.

Implemented tool families include:

- `ui`, `history`, `todos`, `context`, `pwd`, and runtime helpers
- `file:*` tools for project workspace file access
- `workspace:file` for managed imported files, hashes, text/PDF/image operations, rendering, search, and sync
- `canvas` for model-facing canvas inspection, mutation, and image capture
- sub-agent lifecycle/execution/todo assignment tools
- `workspace:layout` for internal window size/position inspection and arrangement
- `usecases` for listing, showing, and searching packaged use-case documents

External browser/search tools currently return explicit unavailable responses unless a real backend is wired.

## Model Context (Main Agent)

Each main-agent request includes:

- system context prompt with authoritative todo summary + current todo list
- active provider and active model from DB-backed settings
- optional user session instruction (`userModelInstruction`)
- last 20 persisted conversation messages from DB
- enabled tool IDs from `AgentToolConfigService`
- request metadata (`sessionId`, `agent`, optional `requestId`)
- strict tool-name guard message (exact callable function names)

Sub-agent requests receive their own history, role metadata, and enabled tool set. Use-case documentation is not injected wholesale into prompts; enabled agents can query it through the `usecases` tool.

## Use-Case Documentation

User-visible functions are documented under `docs/usecases/`. The use-case catalog is packaged into the build and exposed through the `usecases` tool so agents can answer product-behavior questions from maintained documentation.

Each use-case document includes a `## Tool Calls` section. If a workflow has no tool-call path, the section explicitly says `None.`

## Roadmap

### Completed
- [x] Spring AI migration to current stable starter and tool-calling path
- [x] Persistent conversation/todos/sub-agent/tool history in SQLite
- [x] Tool registry + event bus + conversation tool-call rendering
- [x] Session settings and user instruction persistence
- [x] Streaming response path in conversation UI
- [x] OpenAI/OpenAI-compatible provider support with provider-specific model selection
- [x] Optional bearer authentication for secured Ollama endpoints
- [x] Managed workspace files with Files panel and workspace file tool calls
- [x] JHotDraw-based editable canvas with persistence, workspace save/open, export, and model-facing canvas tools
- [x] Draggable/resizable internal workspace windows with persisted layout
- [x] Packaged use-case documentation catalog and model-facing query tool

### In Progress
- [ ] File-size modularization (target: max 300 LOC per source file)
- [ ] Package-size cleanup and stronger architectural boundaries
- [ ] Additional GUI integration coverage

### Planned
- [ ] Browser/search real backend integration (currently explicit unavailable tool responses)
- [ ] Local OCR backend for scanned PDFs

## License

MIT License
