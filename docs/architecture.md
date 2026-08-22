# Architecture

## Overview

Visual Agent is a Compose Multiplatform desktop application with a transport boundary between presentation and Spring-managed services.
Its long-term goal is to give the model as many tools as possible so it can visualize its
own output. Today those surfaces include an editable canvas, managed workspace files, a
todo/sub-agent system, and a conversation panel; future work will add more rendering and
interaction surfaces.

The runtime uses Spring AI for model interaction and tool-calling, and Spring Data JPA on SQLite as the persistent state source.
The desktop host is launched from `:desktop` and the server-only `:application` entry point can
run without Compose. In desktop mode, `:desktop` starts exactly one non-web Spring context from
the `:application` module in the same JVM; it does not start a second server. The standalone
`:application` entry point is an alternative process for deployments that do not use Compose.
`:protocol` owns the versioned request, event, streaming, lifecycle, and gRPC session contracts.
The UI receives only protocol ports; it never receives Spring beans.

## Runtime Layers

1. Presentation: Compose Multiplatform shell in `:ui`, started by the `:desktop` host. It
   depends only on `:protocol` and renders through `ApplicationPort` and its child ports.
2. Protocol: `:protocol` contains the generated gRPC contract and stable version/error enums.
3. Application: `AgentManager` orchestrates chat, streaming, history, todos,
   sub-agents, and the autonomous loop. It is composed of three internal
   ops classes (`AgentManagerConversationOps`, `AgentManagerLifecycleOps`,
   `AgentManagerAutonomyOps`).
4. Provider: `ConfiguredLLMProvider` is the `@Primary` Spring `LLMProvider` bean.
   It resolves each request through the SQLite-backed
   `agent/provider/ProviderCatalogService` (preference key
   `llm.provider.catalog.v1`) and dispatches to `OllamaClient` or
   `OpenAiClient`. Provider adapters: `OLLAMA`, `OPENAI_COMPATIBLE`.
5. Tools: every tool is a `@Component` implementing
   `agent/tools/VisualAgentTool`. `ToolRegistry` adapts them to Spring AI
   `ToolCallback`s with STARTED/FINISHED events on
   `agent/tools/ToolEventBus`. `VisualAgentTool.managesExecution = true`
   opts out of the generic async/timeout wrapper (used by the
   sub-agent execution tools).
6. Orchestration: `orchestration/AutonomousCoordinator` runs the
   autonomous loop, reachable only through `AgentManagerAutonomyOps`. It
   uses `AutonomousTaskPlanner` (todo expansion + worker selection) and
   `UxSeedTasks.all()` as the default UX backlog. Concurrency is gated by
   `SubAgentJobScheduler` keyed off `AppConfig.maxParallelSubAgents`.
7. Persistence: JPA-backed stores on SQLite, with Flyway migrations
   (`V1__initial_knowledge_schema.sql`, `V2__workspace_files.sql`) and a
   native FTS5 search path for conversation history with a `LIKE`
   fallback. `KnowledgePersistenceConfig` creates the SQLite `DataSource`
   (Hikari, `maximumPoolSize = 1`, WAL, `busy_timeout=5000`).

## Current Implemented Flow

1. The desktop shell opens immediately and renders a safe, centered, frameless splash window. The main window is not created until startup is ready.
2. The host starts the local Spring server on a background dispatcher in the same JVM, resolves
   one `ApplicationPort`, and passes only protocol DTOs/ports to `:ui`. A future remote endpoint
   can replace this local adapter without changing panel code.
3. The user types a message in the conversation panel and presses
   `Enter` or clicks the send button.
4. `ConversationPanel` calls the protocol-owned conversation port, whose
   Spring adapter delegates to the application service and maps application
   models to UI DTOs. It updates a temporary assistant turn in place as chunks arrive. The
   in-flight activity indicator in the header pulses for the duration
   of the request.
5. `AgentManager` builds a `ChatRequestContext` with:
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
6. The active provider maps context to its Spring AI prompt and model
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

7. Spring AI executes tool calls through registered `ToolCallback`s.
   Each STARTED/FINISHED event is published on the `ToolEventBus`; the
   in-flight activity indicator increments while a tool is in flight.
8. Tool events are persisted into conversation history as compact
   "Tool `<id>` · <status> · <first line>" entries with structured
   metadata (tool id, function name, status, duration, input, result).
9. UI reflects streaming text, tool activity, in-flight indicator, and
   stored history.

## In-Flight Activity Indicator

`:ui` hosts `InFlightStateHolder`, the single mutable holder for "agent
is waiting on something" that aggregates protocol activity sources:

- `ConversationPanel` chat streams (`markStreamStart/End` around the
  conversation protocol port)
- `SubAgentsPanel` jobs (`markAgentStart/End` from the
  `onRunningChanged` callback)
- `ActivityPort` STARTED/FINISHED events, kept in sync by the UI
- settings and provider refreshes reported through protocol callbacks

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

Main-agent tool set (`agentToolConfigService.mainAgentTools()`):
`agent:list`, `agent:show`, `agent:create`, `agent:update`, `agent:delete`,
`agent:log`, `todos`, `workspace:file`, `workspace:mime`,
`workspace:download`, and `javascript:execute`.

Sub-agent role-based sets (`AgentToolConfigService.toolsFor(agent)`,
default templates `researcher`, `coder`, `analyst`): `todos` plus the
non-agent tool IDs applicable to the configured role.

Globally disabled tools are kept in the `tools.disabled.global`
preference as a newline-separated blocklist.

Common tools: `ui`, `history`, `todos`, `context`, `pwd`, `manual`,
`usecases`, `file:read`, `file:list`, `file:glob`, `file:grep`,
`file:write`, `file:edit`, `terminal`, `sleep`, `browser` (placeholder
that returns "not configured"), `search` (placeholder that returns
"not configured"), `workspace:layout`, `workspace:file`, `workspace:mime`,
`workspace:download`, `canvas`.
`javascript:execute` is available to the default role templates for complex
deterministic multi-tool filtering, aggregation, and large CSV, string, or
Markdown assembly. Execution errors are returned to the model as compact,
actionable categories so it can correct the script or arguments.

### Sandboxed JavaScript tool execution

`javascript:execute` is implemented in the application server with a fresh
GraalJS context for every request. The context exposes only request-scoped
`tools.call`, `tools.list`, `tools.describe`, a hardened `workspace.write/read/delete`
file API, and bounded simulated console methods. Calls are delegated through the existing `ToolRegistry`, preserving
the normal allowlist, lifecycle events, cancellation, and tool safeguards.
The context denies host classes, host objects, IO, native access, process
creation, networking, and polyglot access. Result, timeout, tool-call,
concurrency, logging, and recursion limits are enforced before the final
return value is sent to the model; script source length is not capped.
Intermediate tool responses stay inside the guest runtime. The tool may load a
workspace-relative JavaScript file through the hardened workspace boundary and execute
it in the same sandbox; absolute paths and traversal components are rejected.

Managed workspace mutations are performed by server-owned workspace tools or the hardened
JavaScript workspace file API. In particular,
`workspace:file` deletion removes the filesystem entry and its metadata; `deleteDirectory` only
removes empty directories unless `recursive=true` is explicitly requested. Model runtimes must
not delete registered workspace files through shell commands.

## UI/Application Runtime Boundary

`ApplicationConnection` is the desktop lifecycle boundary; it performs readiness before exposing
the `ApplicationPort` consumed by the presentation. Its children cover conversation
request/response and streaming, todos, agents, providers/settings,
workspace files, canvas operations, activity events, layout persistence, and lifecycle/cancel
commands. Spring adapters in `application/server/` translate application services and internal
event buses into these contracts. The local connection performs a gRPC in-process hello/readiness
exchange without a network hop; the same session contract is the transport seam for a future
remote deployment.

The dependency direction is enforced by `verifyModuleDependencies` and a source-import scan:

```text
:desktop ──► :application + :ui ──► :protocol
```

`:application` never imports Compose classes, and `:ui` never imports Spring, JPA, providers,
tools, persistence, or application implementation types.

Every connection outside the presentation process is proxied by the server: provider/network
requests, persistence, workspace filesystem access, tool execution, and remote services are
owned by `:application` and exposed through protocol ports. The UI does not open those
connections itself.

When `visualagent.server.remote-endpoint=grpcs://host:port` is configured, the desktop host
selects that endpoint, performs a TLS gRPC protocol handshake, and reports a safe failure if
the remote transport is unavailable. It never silently starts a local server in that mode.
The complete remote `ApplicationPort` client is intentionally a follow-up deployment step;
local operation uses the same protocol boundary with an in-process adapter.

The standalone gRPC server is disabled by default (`visualagent.server.port=0`). If explicitly
enabled, it binds to loopback unless a future authenticated listener is configured; non-loopback
binding is rejected rather than exposing an unauthenticated service.

Gradle desktop and standalone-server tasks run with the repository root as working directory so
the configured `./data/visual-agent.db` remains stable across the refactored modules. Existing
preferences are therefore read from the same database rather than a module-local `data/` copy.
Those tasks also set `spring.output.ansi.enabled=ALWAYS`, because Gradle's child JVM does not
expose an interactive `System.console()` even when its output is attached to a terminal.

## UI Architecture Notes

- Main shell: `VisualAgentComposeApp` with a left rail, a
  header that shows Provider, Model, Beans, and the in-flight
  activity indicator, and a single horizontal row of workspace
  panels whose visibility, user-defined order, and individual
  widths are persisted.
- The six panels are Conversation, Todos, Files, Subagents, Settings,
  and Canvas. Each is rendered as a Compose `PanelSection` /
  `PanelContentCard` from the shared widgets in
  `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/workspace/ComposePanelControls.kt`.
- `ComposeSplitWorkspace` lays out visible panels in one horizontally
  scrollable `ReorderableRow`. Each panel keeps its own
  `preferredWidth`; dragging a panel header reorders the row;
  dragging the resizer between two panels changes only the left
  panel's width and pushes all panels to the right. When the
  combined widths exceed the viewport, the row scrolls horizontally
  via mouse wheel, scroll arrows, or a horizontal scrollbar.
  `WORKSPACE_PANEL_GAP = 16`.
- `Cmd/Ctrl+1..6` focuses panels; `Cmd/Ctrl+K` opens the internal
  command palette; `Esc` closes the palette.
- Workspace toolbar action groups use icon-only buttons with descriptive
  tooltips and accessibility descriptions. Settings authentication flows keep
  labeled controls where their text communicates the authentication action.
- Internal modals (`ComposeModalHost` with `ComposeConfirmationModal`,
  `ComposeInfoModal`, `ComposeContentModal`) replace native dialogs
  for destructive confirmations.
- Markdown rendering: messages are passed 1:1 to CommonMark with
  `AutolinkExtension`; no pre-normalization, rewriting, or heuristic
  transformation. Image nodes are resolved through the server-owned
  `ConversationMediaResolver`; Apache Tika validates the payload MIME type
  before the UI decodes it. `workspace:` and `server-file:` identify
  server-managed files (unprefixed paths use the same server route), while
  `client-file:` is the only source resolved by the desktop client. Persisted
  canvas snapshots cross the same protocol boundary as validated image
  attachments.
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

The build includes automated `locAndPackageSizeCheck` during `check` and blocks files above the
300 effective-LOC limit. `desktopApiUsageCheck` blocks any new `java.awt`/`javax.swing`/
JavaFX/AWT image I/O source references. The single
`-Djava.awt.headless=false` JVM arg in `build.gradle.kts` is
whitelisted; it is required for Compose Desktop to discover screen
density in non-headless desktop mode.
