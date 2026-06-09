# Architecture

## Overview

Visual Agent is a JavaFX desktop application with Spring-managed services.  
The runtime uses Spring AI for model interaction and tool-calling, and Spring Data JPA on SQLite as the persistent state source.

## Runtime Layers

1. Presentation: JavaFX `MainWindow` + panel controllers.
2. Application: `AgentManager` orchestrates chat, streaming, history, todos, and sub-agents.
3. Provider: `LLMProvider` abstraction with `OllamaClient` implementation.
4. Tools: `ToolRegistry` + `VisualAgentTool` implementations + `ToolEventBus`.
5. Persistence: JPA-backed stores on SQLite, with Flyway migrations and a native FTS5 search path for conversation history.

## Current Implemented Flow

1. User sends message in `ChatPanel`.
2. `MainWindow` forwards to `AgentManager` (`sendMessage` or `streamMessage`).
3. `AgentManager` builds a `ChatRequestContext` with:
   - recent persisted history (paged),
   - active model and runtime metadata,
   - enabled tools for the active agent.
4. `OllamaClient` maps context to Spring AI `Prompt` + `OllamaChatOptions`.
5. Spring AI executes tool calls through registered `ToolCallback`s.
6. Tool events are emitted (`STARTED`/`FINISHED`) and persisted into conversation history.
7. UI reflects streaming text, tool activity, and stored history.

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
- `terminal`
- `context`
- `pwd`
- `browser` (explicit unavailable backend response)
- `search` (explicit unavailable backend response)

## UI Architecture Notes

- Main shell: `MainWindow` (FXML) with panel switching and keyboard shortcuts.
- Conversation UI:
  - markdown rendering,
  - streaming updates,
  - tool call entries,
  - persisted history with paged loading.
- Session UI drives model selection and session preferences.
- Todo panel and conversation both reflect persisted todo state.

## Current Constraints

The following source files still exceed the 300 LOC target and are marked for modularization:

- `agent/AgentManager.kt`
- `ui/panels/ChatPanel.kt`
- `ui/MainWindow.kt`
- `agent/OllamaClient.kt`

The build now includes automated LOC/package-size checks during `check`; violations are currently reported as warnings (non-blocking) until the modularization work is completed.
