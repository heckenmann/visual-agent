# API Reference

## Core Provider Contract

`LLMProvider` is the only model-provider interface used by application code.

Primary methods:

- `chat(messages: List<Message>)`
- `chat(request: ChatRequestContext)`
- `stream(messages: List<Message>)`
- `stream(request: ChatRequestContext)`
- `vision(image, prompt)` for provider/model image analysis
- `embeddings(text)`
- `checkConnection()`
- `getModels()`
- `getModelDetails(modelName)`

## Request Model

`ChatRequestContext` is the central request type:

- `messages`: provider-neutral chat messages
- `provider`: optional provider override for one agent/request
- `model`: optional explicit model override
- `variant`: optional model variant
- `parameters`: optional temperature, Top P, and maximum output token overrides
- `options`: open provider-specific option map
- `enabledTools`: canonical tool IDs allowed for this call
- `metadata`: runtime context (`sessionId`, `agent`, `requestId`, etc.)

This is what `AgentManager` sends into the provider.

## Conversation Turn Structure

Conversation history preserves provider assistant-turn boundaries. An assistant
message that declared tools is stored as a structural assistant turn with a
stable message ID. Each tool execution is a separate child message carrying
`parentAssistantTurnId` and its provider declaration `turnOrder`. The parent
relationship and order are persisted as typed columns, not inferred from row
adjacency or decoded from UI metadata.

Streaming deltas use `ConversationStreamUpdate(assistantTurnId, textDelta)`.
The turn ID remains stable from the first streamed delta through persisted
history, so separate provider rounds never share one transient UI message.
Conversation protocol messages and history pages carry `parentAssistantTurnId`,
`turnOrder`, and `assistantToolTurn`; gRPC chat deltas carry the same assistant
turn ID. History paging expands any selected parent/tool row to include its
complete group.

Within one provider request, Spring AI's tool loop retains its native structured
assistant/tool-call/result history and provider call IDs. Across completed user
requests, persisted tool rows are projected as bounded historical execution
summaries for context; these summaries are not used as a substitute for native
tool-call protocol history within an active provider loop. Legacy tool rows
without an explicit parent remain standalone.

## Provider Implementations

`ConfiguredLLMProvider` is the primary `LLMProvider` bean injected into UI and agent orchestration code. It resolves each request through the H2-backed `ProviderCatalogService` and delegates to the configured adapter.

- `llm.provider=ollama`
- `llm.provider=openai`

Model selection is provider-specific:

- `ollama.model`
- `openai.model`

Sub-agents may inherit session defaults or persist their own provider, model, variant, and options. Options are merged in provider/model/agent/variant order. Deprecated, disabled, blacklisted, and non-whitelisted models are excluded.

`ProviderCatalogService.resolve(providerId, modelId, variant, agentOptions)` returns a `ResolvedModelConfig` whose model is the first matching selectable model. When the caller does not pass an explicit `modelId` and the provider's persisted `defaultModel` is no longer in the selectable set, the resolver falls back to the first selectable model instead of forwarding the stale id. Explicitly requested model ids are honored as-is and only blocked by the blacklist/whitelist rules.

Ollama endpoints also use:

- `ollama.local.url`
- `ollama.api.key` (optional)

OpenAI-compatible endpoints also use:

- `openai.base.url`
- `openai.api.key`

Current product decision: `ollama.api.key` and `openai.api.key` are stored plaintext in H2 `user_preferences`. Keys are excluded from configuration exports and must never be included in model context, tool output, or logs.

### Ollama

`OllamaClient` is implemented on top of Spring AI:

- model calls via `ChatModel`
- options via `OllamaChatOptions`
- tool integration via request-scoped `ToolCallback`s from `ToolRegistry`
- model discovery, details, and embeddings via the shared `OllamaApi`

`OllamaApiConfiguration` creates the shared API client from `ollama.local.url`. If `ollama.api.key` is non-blank, both the Spring `RestClient` and reactive `WebClient` paths add `Authorization: Bearer <key>`. The key is read for each request so changes apply without rebuilding the client. The Base URL is fixed when the bean is created and therefore requires an application restart after modification.

Unknown tool-call names are handled with a structured recovery path and a fallback response listing available function names.

### OpenAI

`OpenAiClient` is implemented on top of Spring AI OpenAI:

- model calls via dynamically created OpenAI `ChatModel`
- options via `OpenAiChatOptions`
- tool integration via request-scoped `ToolCallback`s from `ToolRegistry`
- model listing through the OpenAI-compatible `/v1/models` endpoint

OpenAI model details are intentionally minimal because OpenAI-compatible model-list responses do not provide Ollama-style metadata.

## Tool Calling

Tools are defined through app-level `ToolDefinition` and executed through `VisualAgentTool`.

`ToolRegistry`:
- filters by allowed `ToolId`s per request
- maps canonical IDs to function names
- builds Spring AI `ToolCallback`s
- emits events through `ToolEventBus`
- enforces default per-call timeout from `AppConfig.timeoutSeconds`
- supports per-call timeout override via tool input: `{"timeoutSeconds": N}`
- supports async execution via tool input: `{"async": true}` (returns immediate scheduled result; `FINISHED` event follows later)
- lets `VisualAgentTool.managesExecution = true` opt out of the generic async/timeout wrapper; the sub-agent execution tools (`AgentStartTool`, `AgentMessageTool`) use this to call `AgentManager.runAgentJob` / `enqueueAgentJob` directly

For main-agent calls, the tool-calling loop persists each provider assistant
turn before its calls execute. Tool lifecycle events update stable child rows
under that turn, ordered by the provider's original declaration sequence.
Intermediate assistant prose and the later post-tool answer remain separate
conversation messages.

### Main-agent tool set

The main agent receives the sub-agent definition IDs (`agent:*`), `todos`,
managed workspace tools, `memory`, `skills`, and `javascript:execute` through
`AgentToolConfigService.mainAgentTools()`. It delegates repository file,
browser, search, history, manual, use-case, and canvas work to
sub-agents. JavaScript may call only tools enabled for this request through
the shared registry; it does not bypass that delegation or permission policy.

| Tool ID | Action | Purpose |
|---|---|---|
| `agent:list` | `get` | Returns active/queued counts and a per-agent line. |
| `agent:show` | `get` | Returns configuration, current work, tools, and recent log entries for one sub-agent. |
| `agent:create` | `create` | Creates a sub-agent from a named template. |
| `agent:update` | `update` | Updates name, role, and configuration of an existing sub-agent. |
| `agent:delete` | `delete` | Removes a sub-agent. |
| `agent:log` | `get` | Returns up to 50 persisted work-log entries for one sub-agent. |
| `todos` | multiple | Lists and manages todos, including assigning work to a sub-agent and retrieving a stored result. |
| `javascript:execute` | `execute` | Runs sandboxed inline JavaScript or a workspace-relative JavaScript file for complex logic or large CSV/Markdown output; `workspace.write/read/delete` provides hardened text access, and actionable execution errors are returned to the model. |

### Sub-agent role-based tool sets

`AgentToolConfigService.toolsFor(agent)` selects a tool set by
matching the agent's name or role to a default template:

- `researcher`: read-only `workspace:file` actions, history, context, todos,
  manual, usecases, sleep, browser, search, and canvas.
- `coder`: adds read-write `workspace:file` actions; raises the default
  `maxTurns` to 8.
- `analyst`: same as `researcher` minus `browser` and `search`,
  plus review-friendly tools.

`tools.disabled.global` (preference) is a newline-separated blocklist
applied to all agents.

### Common tool inventory

The remaining tool IDs are available to sub-agents based on the
role-based sets above and the global blocklist:

- `ui`: get/set of theme, font size, active provider, default model,
  OpenAI base URL, streaming/thinking/compaction toggles. Reports
  API keys only as "configured / not configured".
- `manual`: built-in manual pages (index, markdown reference, and one
  page per registered tool with underscored function names).
- `usecases`: actions `list`, `show`, `search` over the packaged
  `docs/usecases/*.md` catalog.
- `history`: actions `load` (paged) and `search` (bounded `LIKE`)
  fallback) of conversation messages.
- `todos`: actions `list`, `get`, `add`, `update`, `complete`,
  `cancel`, `clear`, `assignToAgent`, `get-result`. `add` requires a
  valid `assignedAgentId`.
- `context`: active provider/model and request metadata plus bounded OS/JVM, memory, and optional
  physical-memory/CPU diagnostics for the Visual Agent server process. A separate desktop client's
  JVM is not included; in embedded desktop mode the server may share the desktop process.
- `system:time`: current Visual Agent server time in UTC and server-local time, with optional
  timezone conversion.
- `network:dns`: resolve A/AAAA addresses from the server; defaults to the
  system resolver and optionally accepts a DNS server IP and port.
- `network:reverse-dns`: query PTR names for one IPv4/IPv6 literal, using the system resolver or an
  explicitly selected DNS server.
- `network:tcp`: test one server-side TCP connection to a single host and port; it does not scan
  port ranges.
- `network:ping`: run bounded best-effort server-side reachability checks with the JDK
  `InetAddress.isReachable` API. The JVM may use ICMP or a platform-specific fallback; a missing
  response does not prove the target is down.
- `network:traceroute`: trace one server-side route with bounded platform utilities on Linux,
  macOS, or Windows. It returns parsed numeric hops where available; silent hops are not proof of
  a network failure.
- `network:interfaces`: list bounded interface flags, MTU, and numeric addresses using the JDK;
  hardware addresses and unrelated host data are omitted.
- `network:http`: inspect one HTTP(S) endpoint using the JDK HTTP client; reports status, safe
  redirect authorities, timing, and limited metadata without response bodies or credential headers.
- `network:tls`: inspect one TLS endpoint using the JDK JSSE implementation and the server's
  platform plus managed CA trust roots; reports verification status and bounded public certificate
  metadata without exposing certificate key material. This is separate from DNS and reverse-DNS tools.
- `security:truststore`: main-agent-only tool for listing, inspecting, importing, and removing CA
  certificates from the managed server trust store. It is disabled by default; changes take effect
  in long-lived clients after a server restart.
- `security:keystore`: main-agent-only tool for listing and inspecting managed key entries,
  generating CA/server certificates, removing entries, and exporting public certificates. Private
  keys and passwords are never returned. The managed alias can be used by the optional gRPC server.
- `system:threads`: request a bounded JVM thread summary, deadlock report, or thread dump filtered
  by state. Thread dumps limit both thread count and stack frames and omit thread-local values.
- `system:filesystem`: inspect capacity and access status for the server data root, managed
  workspace, database directory, and temporary directory through JDK NIO. Results explicitly refer
  to the server host and omit the configured paths.
- `system:client-runtime`: return the desktop client's JVM and OS snapshot explicitly attached to
  the current chat request. This is separate from server-side `context`, `system:threads`, and
  `system:filesystem` diagnostics; no client snapshot is persisted or exposed by ordinary context.
- `sleep`: blocks the calling coroutine for `seconds.coerceIn(0, 300)`.
- `browser`: placeholder that returns "not configured" until a real
  backend is wired (issues #16 and #40).
- `search`: placeholder that returns "not configured" until a real
  backend is wired.
- `workspace:layout`: actions `get` (screens, main window, desktop,
  panel positions) and `set` (replace panel positions). Persists
  changes and notifies the live Compose workspace.
- `workspace:file`: all model-visible filesystem access. Its root-ID based
  actions include list, glob, grep, search, readText, writeText, edit,
  createDirectory, delete, MIME detection, and managed-workspace media
  operations. It never accepts a host filesystem path.
- `workspace:download`: download an HTTP(S), FTP, SFTP, or SCP
  resource into `workspace/downloads` or another workspace-relative
  directory, then register it with managed metadata.
- `javascript:execute`: execute bounded JavaScript using only the tools
  enabled for the current request. Use `tools.call(name, arguments)` for
  multi-tool processing and return strings, Markdown, or JSON-compatible
  values. The sandbox has no direct host, filesystem, process, network,
  JVM, environment, or credential access.
- `skills`: search, read, create, update, and delete reusable Markdown skills.
  Search is bounded by database-neutral `LIKE` matching; model reads update persisted
  read statistics, while user-panel reads do not.

### Canvas Tool

The `canvas` tool is available to sub-agents, not to the main orchestration agent. It lets model calls inspect and mutate the editable JVM canvas model canvas through Compose Multiplatform-safe service calls.

Supported actions:

- `get`: returns `figureCount`, `zoomPercent`, `gridVisible`, and ordered figure summaries.
- `clear`: removes all figures.
- `drawText`: requires `text`, `x`, and `y`; optional `color`.
- `drawRect`: requires `x`, `y`, `width`, and `height`; optional `fillColor`, `strokeColor`.
- `drawLine`: requires `x1`, `y1`, `x2`, and `y2`; optional `color`, `width`.
- `drawStroke`: requires `points` (array of `{x, y}` objects, at least
  two entries); optional `color`, `width`. Freehand pen tool.
- `drawCircle`: requires `centerX`, `centerY`, and `radius`; optional `fillColor`.
- `insertImage`: requires a workspace-relative `path`; paths outside the workspace are rejected.
- `select`: optional `index`; selects one figure or clears selection when omitted.
- `selectAt`: requires `x` and `y`; selects the top-most figure at the coordinate.
- `moveFigure`: requires `index`, `deltaX`, and `deltaY`; moves one figure.
- `resizeFigure`: requires `index`, `width`, and `height`; resizes one figure.
- `deleteFigure`: requires `index`; deletes one figure and reindexes remaining figures.
- `saveDocument`: optional `name`; serializes the editable canvas as a managed `.canvas` workspace file.
- `openDocument`: requires `id` or `path`; loads a managed `.canvas` workspace file into the editable canvas.
- `captureImage`: optional `format` (`png`); renders the current canvas and stores an immutable image entry in persisted conversation history.

Example:

```json
{
  "action": "drawRect",
  "x": 40,
  "y": 60,
  "width": 180,
  "height": 100,
  "fillColor": "#ffffff",
  "strokeColor": "#1f6feb"
}
```

### Workspace Layout Tool

The `workspace:layout` tool is available to sub-agents, not to the main orchestration agent. It lets model calls inspect screens, the main window, the internal desktop, and semantic workspace panel slots derived from the persisted user-defined panel order.

### Workspace File Tool

The `workspace:file` tool is available to sub-agents. It operates on files imported into the
server-owned managed workspace directory below the configured H2 database.

Supported actions:

- `listRoots`: returns the managed workspace plus explicitly granted roots as opaque IDs.
- `list`: returns immediate entries below the selected opaque root and relative path.
- `glob` and `grep`: find regular files or bounded matching text lines below an authorized root-relative path.
- `search`: searches managed metadata/content or granted-root text content without exposing native paths.
- `search`: requires `query`; searches metadata and bounded text/PDF content.
- `info`: requires `id` or `path`; returns persisted metadata.
- `delete`: removes an authorized file or directory through its filesystem owner; it never receives a host path.
- `deleteDirectory`: requires `path`; deletes an empty managed directory. Add `recursive:true` explicitly to delete nested files and directories, including their persisted metadata. The workspace root cannot be deleted.
- `sync`: reconciles workspace files on disk with persisted metadata and reports added, updated, and removed records.
- `hash`: requires `id` or `path`; computes the current SHA-256 hash from file bytes.
- `readText`: requires an ID/path in the managed workspace or an opaque root ID plus relative path; reads bounded UTF-8 text content.
- `writeText` and `edit`: create/update text through the same owner-side authorization boundary; edit requires exactly one old-text occurrence.
- `mime`: detects a content-derived MIME type from bounded bytes through the selected root.
- `extractPdfText`: requires `id` or `path`; extracts bounded PDF text and caches it.
- `renderPdfPage`: requires `id` or `path` and optional `page`; renders extracted page text into a generated PNG preview under the managed workspace.
- `imageInfo`: requires `id` or `path`; returns dimensions, MIME type, size, and hash.
- `imageBytes`: requires `id` or `path`; returns bounded base64 image bytes.
- `analyzeImage`: requires `id` or `path` plus `prompt`; sends the image to the active provider vision path.

Imported files are not injected into model context automatically. The model must request content explicitly through this tool.
Saved canvas documents are regular managed workspace files with MIME type `application/vnd.visual-agent.canvas+xml`, so they can be listed, searched, hashed, renamed, deleted, read, and reopened like other workspace files.

### Workspace Transfer Tools

`workspace:download` accepts a remote `source` plus optional workspace-relative
`directory` and safe `filename`. It rejects credentials in model-provided
sources, redirects, private network targets, unsupported protocols, and
incomplete transfers. Downloads have no application-imposed size limit. SFTP
and SCP remain separate protocols;
both are supported with their matching server-side adapter. HTTP(S) downloads use Spring's
`RestClient` with the server-managed `SslBundle` trust configuration; FTP and SSH-based transfers do
not use TLS bundles.

### Use Cases Tool

The `usecases` tool is available to sub-agents. It exposes the packaged `docs/usecases/*.md` catalog from runtime resources, with a filesystem fallback during local development.

Supported actions:

- `list`: returns use-case IDs, titles, and packaged file names.
- `show`: requires `id` or `file`; returns one use-case document.
- `search`: requires `query`; searches IDs, titles, and document content.

Use this tool when the user asks whether Visual Agent supports a function, where a button is documented, or how an implemented workflow is expected to behave.

## Event Surfaces

Tool execution emits `ToolCallEvent` phases:

- `STARTED`
- `FINISHED`

Main consumers:
- conversation UI (activity + history rendering)
- persistence path (`AgentManager.recordToolCall`)

## Data Types in Active Use

- `Message`
- `ChatResponse`
- `ShowResponse`
- `ModelDetails`
- `ToolResult`

All are provider-neutral at application boundaries.

## Known API Constraints

- `vision()` requires a provider/model combination that supports image input; unsupported combinations return provider-level failures. The Codex CLI adapter sends inline `data:` image inputs through the Codex app-server protocol.
- `browser` and `search` tools intentionally return unavailable results until backends are integrated.

## Activity Surface

`ui/compose/ActivityIndicator.kt` exposes `InFlightStateHolder`, the
single mutable holder for "agent is waiting on something" that
aggregates chat streams, sub-agent jobs, tool STARTED/FINISHED
events, and settings refreshes. The header `InFlightIndicator` is
the only visual consumer and renders 1–3 pulsing dots whose period
shortens with the number of in-flight activities. Panels call
`markStreamStart/End`, `markAgentStart/End`, and
`setSettingsLoading(true/false)` from their coroutines; tool events
flow through `rememberInFlightState(toolEventBus)` on the Compose
main dispatcher.
