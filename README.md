# Visual Agent

<p align="center">
  <img src="src/main/resources/icons/visual-agent.svg" alt="Visual Agent icon" width="160">
</p>

Visual Agent is a Kotlin desktop coding agent with Compose Multiplatform UI, Spring Boot/Spring AI, and Spring Data JPA on SQLite.

> **Development notice:** This project was written entirely with LLM assistance and is still under active development.
> Expect rapid changes, incomplete features, and rough edges until the project reaches a stable release.

## Current Status

The Compose migration branch keeps the Spring/agent backend functional and replaces the desktop runtime with Compose Multiplatform. The current UI is a Compose shell with a left rail, semantic workspace panels, rebuilt feature panels, internal modals, command palette, workspace files, and editable canvas support.

| Feature | UI | Backend | Wired |
|---------|----|---------|-------|
| Chat | Compose conversation panel | `AgentManager.sendMessage()/streamMessage()` | Yes |
| SubAgents | Compose sub-agent panel | Persistent sub-agent configs and statuses | Yes |
| Todos | Compose todo panel | Persisted via Spring Data JPA stores | Yes |
| Session | Compose settings surface | `ProviderCatalogService` + runtime adapters | Partial |
| Settings | Compose settings panel | `AppConfig` + DB-backed runtime usage | Yes |
| Files | Compose files panel with FileKit picker | `WorkspaceFileService` + `WorkspaceFileTool` | Yes |
| Canvas | Compose-native editable canvas with workspace save/open | `CanvasOperations` + `CanvasTool` | Yes |
| Workspace Panels | Compose semantic stage/inspector/deck workspace shell | `WorkspaceLayoutService` + `WorkspaceLayoutTool` | Yes |
| Command Palette | Internal Compose overlay opened with `Cmd/Ctrl+K` | Workspace command metadata | Yes |
| Tool Calling | Tool events, minimized tool entries, per-agent tool toggles, sub-agent canvas/workspace/use-case tools | Spring AI `ToolCallback` path | Yes |
| Use Cases | Packaged product use-case catalog available to agents | `docs/usecases/` + `UseCaseTool` | Yes |
| Persistence | Conversation, todos, tools, agents, preferences, history search, workspace metadata | SQLite + JPA + Flyway + FTS5 | Yes |

## Tech Stack

| Component | Version |
|-----------|---------|
| Language | Kotlin 2.4.0 |
| Build System | Gradle 9.6.0 (Kotlin DSL) |
| Application Framework | Spring Boot 4.1.0 |
| AI Integration | Spring AI 2.0.0 |
| UI Framework | Compose Multiplatform 1.11.1 |
| Drawing Framework | Toolkit-neutral canvas operations with Compose editor |
| Database | SQLite 3.53.2.0 (embedded, via JPA/Flyway) |
| HTTP Client | Spring `RestClient` / `WebClient` |
| Serialization | Kotlinx Serialization JSON 1.11.0 |
| Coroutines | Kotlinx Coroutines 1.11.0 |
| Logging | Logback 1.5.x |
| LLM Provider | Spring AI + Ollama/OpenAI (`ChatModel`) |
| Linter | ktlint |
| Namespace | `de.heckenmann.visualagent` |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     MAIN WINDOW (Compose Multiplatform)                    │
├──────────────┬──────────────────────────────────────────────┤
│ LEFT RAIL    │ SPLIT WORKSPACE PANELS                       │
│ navigation   │ deterministic tiled panel slots              │
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
├── canvas/
│   ├── CanvasOperations.kt     # Toolkit-neutral model-facing canvas contract
│   ├── CanvasDocumentCodec.kt  # Versioned editable .canvas document serialization
│   ├── CanvasPngRenderer.kt    # Toolkit-neutral canvas PNG rendering
│   └── InMemoryCanvasService.kt # Compose-migration canvas service with workspace persistence
├── workspace/
│   └── layout/                 # Toolkit-neutral workspace panel layout persistence/tool state
└── ui/
    └── compose/
        ├── VisualAgentComposeApplication.kt # Compose desktop shell and semantic workspace panels
        └── ComposeWorkspaceModels.kt        # Internal-window geometry model
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

## GitHub Packages Artifact

Every successful push to `master` builds the application JAR and publishes it to the repository's GitHub Packages Maven registry. The published version uses the format `0.1.0-master-<short-sha>` (snapshot versions are published unchanged).

Add the package registry to your Gradle build:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/heckenmann/visual-agent")
        credentials {
            username = providers.gradleProperty("gpr.user").orElse(System.getenv("GITHUB_ACTOR")).get()
            password = providers.gradleProperty("gpr.key").orElse(System.getenv("GITHUB_TOKEN")).get()
        }
    }
}

dependencies {
    implementation("de.heckenmann.visualagent:visual-agent:0.1.0-master-SNAPSHOT")
}
```

To use a published artifact you need a GitHub personal access token with `read:packages` scope (or the `GITHUB_TOKEN` secret inside a GitHub Actions workflow).

Compose Desktop must run in non-headless JVM desktop mode. The Gradle application tasks set `java.awt.headless=false` because Compose Desktop uses the JVM `java.desktop` stack internally to discover screen density, even though Visual Agent source code does not use AWT/Swing APIs.

The detailed Compose migration decision and requirement audit is documented in [`docs/compose-migration-audit.md`](docs/compose-migration-audit.md).

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

The workspace file backend supports:

- list, inspect, rename, delete, refresh, search, and filesystem/DB sync
- managed relative paths and SHA-256 hashes
- image metadata without desktop UI image APIs

External source paths are not persisted. Workspace metadata is stored in SQLite, while file bytes remain under the managed workspace folder. Generated files, such as rendered PDF pages, are written under the managed workspace and recorded with metadata and hashes.

## Canvas

The previous visual canvas editor has been removed with the desktop toolkit migration. The current branch uses a toolkit-neutral canvas model rendered and edited through Compose. Current support includes:

- `get`, `clear`, `drawText`, `drawRect`, `drawLine`, `drawCircle`, `insertImage`, `select`, `selectAt`, `moveFigure`, `resizeFigure`, `deleteFigure`, `saveDocument`, `openDocument`, and `captureImage`
- Compose canvas selection, drag-to-move, corner drag-to-resize, and selected-figure deletion
- editable `.canvas` JSON documents persisted under the managed workspace and reopenable from the Files panel or canvas tool calls
- immutable PNG captures rendered from the current toolkit-neutral canvas figures
- no desktop toolkit dependency

## Internal Workspace Panels

Primary panels are hosted as deterministic semantic panels inside the workspace. The first visible panel in the user-defined order gets a dominant stage, supporting panels are placed in an inspector stack, and overflow panels move into a bottom deck. Panel visibility and order are restored on restart, and visible panels are placed into valid workspace slots without overlap or pointer-driven resize states.

Users can change panel order through icon-only actions in each panel header. Panels can be hidden from either the left rail or the panel header.

Agents can query the visible workspace panel slots through the workspace layout tool when that tool is enabled.

## Persistence

The app now uses a Spring Data JPA persistence layer backed by SQLite and Flyway migrations.

- Runtime schema is managed by `Flyway` with versioned SQL migrations.
- Hibernate validates the mapped entities; it does not create or mutate tables in production.
- The existing SQLite data file remains the source of truth for local desktop usage.
- Managed workspace file metadata is stored in SQLite; file bytes are stored under `./data/workspace/` by default.
- Internal workspace panel visibility, order, and layout state are persisted in user preferences.
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
- `workspace:layout` for workspace panel slot inspection
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
- [x] JVM canvas model-based editable canvas with persistence, workspace save/open, export, and model-facing canvas tools
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
