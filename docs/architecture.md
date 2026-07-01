# Architecture

## Overview

Visual Agent is a Compose Multiplatform desktop application with Spring-managed services.
The runtime uses Spring AI for model interaction and tool-calling, and Spring Data JPA on SQLite as the persistent state source.
The Compose Multiplatform shell is launched from `runVisualAgentComposeApplication()`
in `ui/compose/VisualAgentComposeApplication.kt`; the
`VisualAgentApplication.main` entry point exists for tests.

## Runtime Layers

1. Presentation: Compose Multiplatform shell in `VisualAgentComposeApplication` and the
   19-file `ui/compose/` package. Panels receive a `ComposePanelServices` bundle
   resolved once from the Spring context and call `AgentManager` and other
   Spring beans directly. There is no `ViewModel` layer.
2. Application: `AgentManager` orchestrates chat, streaming, history, todos,
   sub-agents, and the autonomous loop. It is composed of three internal
   ops classes (`AgentManagerConversationOps`, `AgentManagerLifecycleOps`,
   `AgentManagerAutonomyOps`).
3. Provider: `ConfiguredLLMProvider` is the `@Primary` Spring `LLMProvider` bean.
   It resolves each request through the SQLite-backed
   `agent/provider/ProviderCatalogService` (preference key
   `llm.provider.catalog.v1`) and dispatches to `OllamaClient` or
   `OpenAiClient`. Provider adapters: `OLLAMA`, `OPENAI_COMPATIBLE`.
4. Tools: every tool is a `@Component` implementing
   `agent/tools/VisualAgentTool`. `ToolRegistry` adapts them to Spring AI
   `ToolCallback`s with STARTED/FINISHED events on
   `agent/tools/ToolEventBus`. `VisualAgentTool.managesExecution = true`
   opts out of the generic async/timeout wrapper (used by the
   sub-agent execution tools).
5. Orchestration: `orchestration/AutonomousCoordinator` runs the
   autonomous loop, reachable only through `AgentManagerAutonomyOps`. It
   uses `AutonomousTaskPlanner` (todo expansion + worker selection) and
   `UxSeedTasks.all()` as the default UX backlog. Concurrency is gated by
   `SubAgentJobScheduler` keyed off `AppConfig.maxParallelSubAgents`.
6. Persistence: JPA-backed stores on SQLite, with Flyway migrations
   (`V1__initial_knowledge_schema.sql`, `V2__workspace_files.sql`) and a
   native FTS5 search path for conversation history with a `LIKE`
   fallback. `KnowledgePersistenceConfig` creates the SQLite `DataSource`
   (Hikari, `maximumPoolSize = 1`, WAL, `busy_timeout=5000`).

## Current Implemented Flow

1. The Compose shell starts with the Spring application context.
2. The user types a message in the conversation panel and presses
   `Enter` or clicks the send button.
3. `ConversationPanel` calls `agentManager.streamMessage(content)` and
   updates a temporary assistant turn in place as chunks arrive. The
   in-flight activity indicator in the header pulses for the duration
   of the request.
4. `AgentManager` builds a `ChatRequestContext` with:
   - system context prompt from `MainSystemPromptComposer` (resume hint,
     authoritative todo counters, current todo list, active provider and
     model, execution policy);
   - optional `userModelInstruction` system message from `AppConfig`;
   - recent persisted history (max 20 messages; older rows are reachable
     via the `history` tool);
   - tool-name guard system message from the active provider's prompt
     factory;
   - `enabledTools = agentToolConfigService.mainAgentTools()`;
   - metadata: `sessionId=main`, `agent=main`, optional `requestId`.
5. The active provider maps context to its Spring AI prompt and model
   options and dispatches to the underlying `ChatModel`.

`ProviderCatalogService` is the authoritative source for dynamic provider
profiles and model metadata. It persists a versioned JSON catalog in
SQLite, migrates legacy settings, filters unavailable models, and
resolves `providerId/modelId` references. When the persisted
`defaultModel` is no longer available on the configured endpoint,
`resolve()` falls back to the first selectable model instead of
forwarding a stale id that would produce 404s from the upstream API.

Sub-agents can override the session provider, model, and variant.
Options are merged in provider, model, agent, then variant order.
Shared generation parameters are translated to Spring AI options,
while supported provider-specific values remain available through an
open options map.

6. Spring AI executes tool calls through registered `ToolCallback`s.
   Each STARTED/FINISHED event is published on the `ToolEventBus`; the
   in-flight activity indicator increments while a tool is in flight.
7. Tool events are persisted into conversation history as compact
   "Tool `<id>` · <status> · <first line>" entries with structured
   metadata (tool id, function name, status, duration, input, result).
8. UI reflects streaming text, tool activity, in-flight indicator, and
   stored history.

## In-Flight Activity Indicator

`ui/compose/ActivityIndicator.kt` hosts `InFlightStateHolder`, the
single mutable holder for "agent is waiting on something" that
aggregates four sources:

- `ConversationPanel` chat streams (`markStreamStart/End` around
  `agentManager.streamMessage`)
- `SubAgentsPanel` jobs (`markAgentStart/End` from the
  `onRunningChanged` callback)
- `ToolEventBus` STARTED/FINISHED events, kept in sync via
  `rememberInFlightState(toolEventBus)` on the Compose main dispatcher
- `SettingsPanel` refreshes (`setSettingsLoading(true/false)` from a
  `LaunchedEffect(settingsLoading)` that derives from `loadingModels ||
  loadingDetails`)

The header `InFlightIndicator` renders 1–3 pulsing dots when
`InFlightState.totalActive > 0` and nothing when it is zero, so no
layout space is reserved while the system is idle. The pulse period
shortens with the number of in-flight activities (1 → 900 ms, 2–3 →
600 ms, 4+ → 400 ms).

## Ollama Client Configuration

`agent/ollama/OllamaApiConfiguration` provides the shared `OllamaApi`
bean. Provider-profile requests create endpoint-specific Spring AI
clients so URL and key changes apply without restart.

- `ollama.local.url` selects the endpoint when the bean is created.
- `ollama.api.key` is optional.
- A non-blank key is sent as `Authorization: Bearer` header on
  synchronous and reactive requests.
- The current key is read for every request, so key changes do not
  require a restart.
- Base URL changes require a restart.
- Raw credentials are not included in model context, tool output, logs,
  or configuration exports.

## Persistence Model

DB-first behavior is used app-wide:

- conversation messages are persisted and reloaded on restart
- todo state is persisted and surfaced to both UI and agent context
- tool call history entries are persisted and rendered in conversation
- sub-agent configurations are loaded from DB and maintained via CRUD
- persistence access is routed through typed stores, not a JDBC facade
- workspace file metadata is persisted in DB while the file bytes live
  on disk under `./data/workspace/`

Values are not treated as long-lived in-memory truth; the database is
the authoritative source.

## Tooling Architecture

Tools are exposed via canonical IDs and mapped to provider-safe function
names inside `ToolRegistry`. The full inventory lives in `AGENTS.md`;
the runtime split is:

Main-agent-only (`agentToolConfigService.mainAgentTools()`):
`agent:list`, `agent:start`, `agent:create`, `agent:update`,
`agent:delete`, `agent:message`, `agent:assign-todo`,
`agent:assign-next-todo`, `agent:assign-all-todos`.

Sub-agent role-based sets (`AgentToolConfigService.toolsFor(agent)`,
default templates `researcher`, `coder`, `analyst`): all other tool
IDs.

Globally disabled tools are kept in the `tools.disabled.global`
preference as a newline-separated blocklist.

Common tools: `ui`, `history`, `todos`, `context`, `pwd`, `manual`,
`usecases`, `file:read`, `file:list`, `file:glob`, `file:grep`,
`file:write`, `file:edit`, `terminal`, `sleep`, `browser` (placeholder
that returns "not configured"), `search` (placeholder that returns
"not configured"), `workspace:layout`, `workspace:file`, `canvas`.

## UI Architecture Notes

- Main shell: `VisualAgentComposeApplication` with a left rail, a
  header that shows Provider, Model, Beans, and the in-flight
  activity indicator, and semantic workspace panels whose visibility
  and user-defined order are persisted.
- The six panels are Conversation, Todos, Files, Subagents, Settings,
  and Canvas. Each is rendered as a Compose `PanelSection` /
  `PanelContentCard` from the shared widgets in
  `ui/compose/ComposePanelControls.kt`.
- `ComposeSplitWorkspace` lays out panels deterministically: 1
  visible panel = full, 2–4 = primary stage with right-side inspector
  stack, 5+ = balanced left/right columns. `WORKSPACE_PANEL_GAP = 16`.
- `Cmd/Ctrl+1..6` focuses panels; `Cmd/Ctrl+K` opens the internal
  command palette; `Esc` closes the palette.
- Internal modals (`ComposeModalHost` with `ComposeConfirmationModal`,
  `ComposeInfoModal`, `ComposeContentModal`) replace native dialogs
  for destructive confirmations.
- Markdown rendering: messages are passed 1:1 to CommonMark with
  `AutolinkExtension`; no pre-normalization, rewriting, or heuristic
  transformation.
- Workspace layout persistence is toolkit-neutral under
  `workspace/layout/`. Preference key `ui.workspace.layout.v1` stores a
  versioned JSON document. The `workspace:layout` tool is the
  model-side accessor.

### Canvas Editor

The toolkit-neutral canvas contract is in `canvas/CanvasOperations.kt`,
implemented by `canvas/InMemoryCanvasService.kt` (the current
Compose-migration backend). `canvas/CanvasPngRenderer.kt` rasterizes
figures to PNG; `canvas/CanvasDocumentCodec.kt` encodes editable
`.canvas` JSON (versioned). The default document lives at
`data/workspace/canvas/current.canvas` and is auto-saved on every
mutation; explicit saves use `canvas.saveDocument(name)` to write a
named managed workspace file under `data/workspace/canvas/`.

Image rendering is in-house: `image/RgbaPngEncoder.kt` (no AWT or
`ImageIO`), `workspace/ImageHeaderReader.kt` for PNG/JPEG/GIF
dimensions, and `workspace/PdfPagePreviewRenderer.kt` for text-only
PDF page previews (PDFBox is used only for text extraction;
`pdfbox.rendering` is forbidden by `desktopApiUsageCheck`).

The Compose canvas surface uses `io.github.xingray:compose-infinite-canvas-core`
for pan/zoom, with a top-level `Canvas` overlay for the pen drawing mode
that maps screen coordinates to canvas coordinates through
`InfiniteCanvasState.viewport.screenToWorld`/`worldToScreen`.

The main orchestration agent does not receive direct canvas tools. It
must create or message a sub-agent, and that sub-agent can use the
`canvas` tool when its configured tool set allows it.

## Current Constraints

The following source files still exceed the 300 LOC target and are
marked for modularization:

- `ui/compose/ComposeConversationPanels.kt`
- `ui/compose/ComposeSettingsPanel.kt`
- `ui/compose/ComposeManagementPanels.kt`
- `ui/compose/ComposeCanvasSurface.kt`
- `ui/compose/ComposeWorkspaceComponents.kt`
- `ui/compose/ComposeWorkspaceModels.kt`
- `ui/compose/VisualAgentComposeApplication.kt`
- `src/test/kotlin/de/heckenmann/visualagent/agent/tools/CanvasToolTest.kt`

The `ui/compose` package also exceeds the 3 000 LOC package target at
5 290 LOC.

The build includes automated `locAndPackageSizeCheck` during `check`;
violations are reported as warnings (non-blocking) until the
modularization work is completed.

`desktopApiUsageCheck` blocks any new `java.awt`/`javax.swing`/
JavaFX/AWT image I/O source references. The single
`-Djava.awt.headless=false` JVM arg in `build.gradle.kts` is
whitelisted; it is required for Compose Desktop to discover screen
density in non-headless desktop mode.
