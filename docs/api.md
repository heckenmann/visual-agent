# API Reference

## Core Provider Contract

`LLMProvider` is the only model-provider interface used by application code.

Primary methods:

- `chat(messages: List<Message>)`
- `chat(request: ChatRequestContext)`
- `stream(messages: List<Message>)`
- `stream(request: ChatRequestContext)`
- `vision(image, prompt)` (currently not implemented in Spring AI bridge)
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

## Provider Implementations

`ConfiguredLLMProvider` is the primary `LLMProvider` bean injected into UI and agent orchestration code. It resolves each request through the SQLite-backed `ProviderCatalogService` and delegates to the configured adapter.

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

Current product decision: `ollama.api.key` and `openai.api.key` are stored plaintext in SQLite `user_preferences`. Keys are excluded from configuration exports and must never be included in model context, tool output, or logs.

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

### Main-agent tool set

The main agent only receives the `agent:*` tool IDs through
`AgentToolConfigService.mainAgentTools()`. The model can only drive
sub-agent work through these tools; it cannot call file, terminal,
browser, search, history, todo, manual, usecases, workspace, or
canvas tools directly. The `MainSystemPromptComposer` execution
policy reinforces this in the system prompt.

| Tool ID | Action | Purpose |
|---|---|---|
| `agent:list` | `get` | Returns active/queued counts and a per-agent line. |
| `agent:create` | `create` | Creates a sub-agent from a named template. |
| `agent:update` | `update` | Updates name, role, and configuration of an existing sub-agent. |
| `agent:delete` | `delete` | Removes a sub-agent. |
| `agent:start` | `start` (sync / `async:true`) | Creates a sub-agent from a template and runs the job sync or enqueues async. |
| `agent:message` | `message` (sync / `async:true`) | Sends a message to an existing sub-agent id, sync or async. |
| `agent:assign-todo` | `assignTodo` | Assigns a specific todo to a sub-agent. |
| `agent:assign-next-todo` | `assignNextTodo` | Assigns the next pending todo to an idle agent. |
| `agent:assign-all-todos` | `assignAllTodos` | Distributes pending todos across idle agents up to `AppConfig.maxParallelSubAgents`. |

### Sub-agent role-based tool sets

`AgentToolConfigService.toolsFor(agent)` selects a tool set by
matching the agent's name or role to a default template:

- `researcher`: read-only file tools, history, context, pwd, todos,
  manual, usecases, sleep, browser, search, workspace:* and canvas.
- `coder`: adds `file:write`, `file:edit`, and `terminal`; raises the
  default `maxTurns` to 8.
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
- `history`: actions `load` (paged) and `search` (FTS5 + `LIKE`
  fallback) of conversation messages.
- `todos`: actions `list`, `get`, `add`, `update`, `complete`,
  `cancel`, `clear`, `assignToAgent`.
- `context`: runtime context (active provider/model/key configured/
  streaming/thinking).
- `pwd`: returns the managed workspace root path.
- `file:read`, `file:list`, `file:glob`, `file:grep`, `file:write`,
  `file:edit`: scoped to the managed workspace root.
- `terminal`: runs `zsh -lc`/`bash -lc`/`sh -c` in the workspace
  root with a 1..30 second timeout and 8 000 character output cap.
- `sleep`: blocks the calling coroutine for `seconds.coerceIn(0, 300)`.
- `browser`: placeholder that returns "not configured" until a real
  backend is wired (issues #16 and #40).
- `search`: placeholder that returns "not configured" until a real
  backend is wired.
- `workspace:layout`: actions `get` (screens, main window, desktop,
  panel positions) and `set` (replace panel positions). Persists
  changes and notifies the live Compose workspace.
- `workspace:file`: list/search/info/sync/hash/readText/extractPdfText/
  renderPdfPage/imageInfo/imageBytes/analyzeImage against the
  managed workspace directory.

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

The `workspace:file` tool is available to sub-agents. It operates on files imported into the managed workspace directory next to the configured SQLite database, defaulting to `./data/workspace/`.

Supported actions:

- `list`: returns imported file IDs, relative paths, MIME types, sizes, timestamps, and SHA-256 hashes.
- `search`: requires `query`; searches metadata and bounded text/PDF content.
- `info`: requires `id` or `path`; returns persisted metadata.
- `sync`: reconciles workspace files on disk with persisted metadata and reports added, updated, and removed records.
- `hash`: requires `id` or `path`; computes the current SHA-256 hash from file bytes.
- `readText`: requires `id` or `path`; reads bounded UTF-8 text content.
- `extractPdfText`: requires `id` or `path`; extracts bounded PDF text and caches it.
- `renderPdfPage`: requires `id` or `path` and optional `page`; renders extracted page text into a generated PNG preview under the managed workspace.
- `imageInfo`: requires `id` or `path`; returns dimensions, MIME type, size, and hash.
- `imageBytes`: requires `id` or `path`; returns bounded base64 image bytes.
- `analyzeImage`: requires `id` or `path` plus `prompt`; sends the image to the active provider vision path.

Imported files are not injected into model context automatically. The model must request content explicitly through this tool.
Saved canvas documents are regular managed workspace files with MIME type `application/vnd.visual-agent.canvas+xml`, so they can be listed, searched, hashed, renamed, deleted, read, and reopened like other workspace files.

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

- `vision()` requires a provider/model combination that supports image input; unsupported combinations return provider-level failures.
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
