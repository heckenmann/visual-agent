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
- `model`: optional explicit model override
- `enabledTools`: canonical tool IDs allowed for this call
- `metadata`: runtime context (`sessionId`, `agent`, `requestId`, etc.)

This is what `AgentManager` sends into the provider.

## Ollama Provider Implementation

`OllamaClient` is implemented on top of Spring AI:

- model calls via `ChatModel`
- options via `OllamaChatOptions`
- tool integration via request-scoped `ToolCallback`s from `ToolRegistry`

Unknown tool-call names are handled with a structured recovery path and a fallback response listing available function names.

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
- `file:read`, `file:list`, `file:glob`, `file:grep`, `file:write`, `file:edit`
- `terminal`
- `sleep`
- `context`
- `pwd`
- `browser` (unavailable placeholder result)
- `search` (unavailable placeholder result)

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
