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

Current tool IDs:

- `ui`
- `manual` (built-in tool/manual pages, including markdown format reference)
- `history`
- `todos`
- `canvas`
- `workspace:layout`
- `file:read`, `file:list`, `file:glob`, `file:grep`, `file:write`, `file:edit`
- `terminal`
- `sleep`
- `context`
- `pwd`
- `browser` (unavailable placeholder result)
- `search` (unavailable placeholder result)

### Canvas Tool

The `canvas` tool is available to sub-agents, not to the main orchestration agent. It lets model calls inspect and mutate the editable JHotDraw canvas through JavaFX-safe service calls.

Supported actions:

- `get`: returns `figureCount`, `zoomPercent`, `gridVisible`, and ordered figure summaries.
- `clear`: removes all figures.
- `drawText`: requires `text`, `x`, and `y`; optional `color`.
- `drawRect`: requires `x`, `y`, `width`, and `height`; optional `fillColor`, `strokeColor`.
- `drawLine`: requires `x1`, `y1`, `x2`, and `y2`; optional `color`, `width`.
- `drawCircle`: requires `centerX`, `centerY`, and `radius`; optional `fillColor`.
- `insertImage`: requires a workspace-relative `path`; paths outside the workspace are rejected.

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

The `workspace:layout` tool is available to sub-agents, not to the main orchestration agent. It lets model calls inspect screens, the main window, the internal desktop, and internal workspace windows. It can also reposition or resize internal windows by ID.

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

- `vision()` currently throws `UnsupportedOperationException` in the Spring AI bridge.
- `browser` and `search` tools intentionally return unavailable results until backends are integrated.
