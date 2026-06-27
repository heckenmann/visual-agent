# Architecture

## Overview

Visual Agent is a Compose Multiplatform desktop application with Spring-managed services.  
The runtime uses Spring AI for model interaction and tool-calling, and Spring Data JPA on SQLite as the persistent state source.

## Runtime Layers

1. Presentation: Compose Multiplatform shell in `VisualAgentComposeApplication`.
2. Application: `AgentManager` orchestrates chat, streaming, history, todos, and sub-agents.
3. Provider: `ConfiguredLLMProvider` routes to the Ollama or OpenAI implementation.
4. Tools: `ToolRegistry` + `VisualAgentTool` implementations + `ToolEventBus`.
5. Persistence: JPA-backed stores on SQLite, with Flyway migrations and a native FTS5 search path for conversation history.

## Current Implemented Flow

1. The Compose shell starts with the Spring application context.
2. Compose panels will call `AgentManager` (`sendMessage` or `streamMessage`) as they are rebuilt.
3. `AgentManager` builds a `ChatRequestContext` with:
   - recent persisted history (paged),
   - active model and runtime metadata,
   - enabled tools for the active agent.
4. The active provider maps context to its Spring AI prompt and model options.

`ProviderCatalogService` is the authoritative source for dynamic provider profiles and model metadata. It persists a versioned JSON catalog in SQLite, migrates legacy settings, filters unavailable models, and resolves `providerId/modelId` references.

Sub-agents can override the session provider, model, and variant. Options are merged in provider, model, agent, then variant order. Shared generation parameters are translated to Spring AI options, while supported provider-specific values remain available through an open options map.
5. Spring AI executes tool calls through registered `ToolCallback`s.
6. Tool events are emitted (`STARTED`/`FINISHED`) and persisted into conversation history.
7. UI reflects streaming text, tool activity, and stored history.

## Ollama Client Configuration

`OllamaApiConfiguration` provides the legacy default client. Provider-profile requests create endpoint-specific Spring AI clients so URL and key changes apply without restart.

- `ollama.local.url` selects the endpoint when the bean is created.
- `ollama.api.key` is optional.
- A non-blank key is sent as an `Authorization: Bearer` header on synchronous and reactive requests.
- The current key is read for every request, so key changes do not require a restart.
- Base URL changes require a restart.
- Raw credentials are not included in model context, tool output, logs, or configuration exports.

## Persistence Model

DB-first behavior is used app-wide:

- conversation messages are persisted and reloaded on restart
- todo state is persisted and surfaced to both UI and agent context
- tool call history entries are persisted and rendered in conversation
- sub-agent configurations are loaded from DB and maintained via CRUD
- persistence access is routed through typed stores, not a JDBC facade

Values are not treated as long-lived in-memory truth; DB is the authoritative source.

## Tooling Architecture

Tools are exposed via canonical IDs and mapped to provider-safe function names inside `ToolRegistry`.

Current tool set:
- `ui`
- `history`
- `todos`
- `file:read`, `file:list`, `file:glob`, `file:grep`, `file:write`, `file:edit`
- `workspace:file`
- `terminal`
- `context`
- `pwd`
- `browser` (explicit unavailable backend response)
- `search` (explicit unavailable backend response)

## UI Architecture Notes

- Main shell: `VisualAgentComposeApplication` with a left rail and semantic workspace panels whose visibility and user-defined order are persisted.
- Conversation, session, todo, files, sub-agent, settings, and visual canvas panels are being rebuilt as Compose components.
- Backend services and model tool contracts remain available while UI parity is restored.
- Workspace layout persistence is toolkit-neutral under `workspace/layout`.

### Canvas Editor

The previous visual canvas editor has been removed with the desktop toolkit migration. The current branch keeps the model-facing canvas contract in toolkit-neutral code:

- `CanvasOperations` defines the stable model-facing canvas API.
- `InMemoryCanvasService` provides temporary backend behavior for tool calls while the Compose visual editor is rebuilt.
- `CanvasTool` lets sub-agents inspect the canvas and add text, rectangles, lines, circles, and workspace-local images.
- Canvas persistence, editable selection, image manipulation, export fidelity, and visual editing are migration follow-up work.

The main orchestration agent does not receive direct canvas tools. It must create or message a sub-agent, and that sub-agent can use the `canvas` tool when its configured tool set allows it.

## Current Constraints

The following source files still exceed the 300 LOC target and are marked for modularization:

- `agent/AgentManager.kt`
- `agent/OllamaClient.kt`

The build now includes automated LOC/package-size checks during `check`; violations are currently reported as warnings (non-blocking) until the modularization work is completed.
