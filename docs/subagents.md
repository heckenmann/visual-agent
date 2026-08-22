# SubAgents

## Current State

Sub-agent management is persisted, integrated into runtime
orchestration, and accessible from the main agent through the
`agent:*` tool set.

Implemented:

- DB-backed sub-agent loading at startup
- default-agent seeding if the DB has fewer than three agents
  (`AgentManagerLifecycleOps` calls `SubAgent.fromTemplate` for each
  `AgentConfig.TEMPLATES` entry: `researcher`, `coder`, `documenter`,
  `reviewer`, `tester`)
- create / update / delete from UI and from the main agent through
  `AgentManager` and the `agent:create` / `agent:update` /
  `agent:delete` tools
- per-agent `name`, `role`, and `status` reflected in the
  `SubAgentsPanel` cards
- per-agent provider / model / variant / temperature / top P / max
  tokens / seed / reasoning effort / verbosity / options / tool set
  inside the persisted `AgentConfig`
- tool policy loading through `AgentToolConfigService`, with role-based
  default sets (`researcher`, `coder`, `analyst`) and a global
  blocklist preference (`tools.disabled.global`)

## Runtime Model

Main components:

- `agent/AgentManager`: orchestration, assignment, lifecycle,
  autonomy. Composed of three internal ops classes:
  - `AgentManagerConversationOps`: `sendMessage` / `streamMessage` /
    sub-agent job dispatch / `buildMainRequest` / `buildMainSystemContextPrompt`.
  - `AgentManagerLifecycleOps`: sub-agent CRUD, todo summary,
    DB persistence, JSON encode/decode of `AgentConfig`.
  - `AgentManagerAutonomyOps`: pass-through to
    `orchestration/AutonomousCoordinator`.
- `agent/SubAgent`: execution unit with role, config, mutable
  `status`, `currentTask`, `currentTodoId`, and per-agent
  `chatHistory`. Exposes `chat(messages, provider, enabledTools)` and
  `performTodo(...)` for the autonomous loop.
- `agent/SubAgentJobScheduler`: FIFO concurrency gate keyed off
  `AppConfig.maxParallelSubAgents`. `enqueue { block, onFinished }`
  returns a job id immediately and fires the callback when the
  coroutine completes.
- `ui/compose/SubAgentsPanel`: UI controls, status rendering, and
  detail editor for provider/model/parameter/option/runtime/tool
  overrides.

`AgentStatus` enum:

- `IDLE`
- `BUSY`
- `OFFLINE`

## Tool Assignment

`AgentToolConfigService.toolsFor(agent)` returns the set of tool IDs
the agent is allowed to call. The set is selected by matching the
agent's `name` or `role` against the default templates:

- `researcher`: read-only file tools, `history`, `context`, `pwd`,
  `todos`, `manual`, `usecases`, `sleep`, `browser`, `search`,
  `workspace:layout`, `workspace:file`, `workspace:mime`,
`workspace:download`, `canvas`.

Agents with `javascript:execute` enabled may use it for complex deterministic
multi-tool filtering, aggregation, and large CSV, string, or Markdown assembly.
The script must call only enabled canonical tools through `await tools.call(...)`,
may use hardened `workspace.write/read/delete(...)` for workspace-relative text files,
and return the complete final value; it cannot access the host directly. If the
execution returns an exception, inspect the actionable category/message, correct
the source or arguments, and retry without repeating the unchanged failure.
- `coder`: adds `file:write`, `file:edit`, and `terminal`; raises
  the default `maxTurns` to 8.
- `analyst`: same as `researcher` minus `browser` and `search`,
  plus review-friendly tools.

The main agent receives `agent:list`, `agent:show`, `agent:create`,
`agent:update`, `agent:delete`, `agent:log`, and `todos` through
`mainAgentTools()`. The `MainSystemPromptComposer` execution policy
reinforces that it must delegate rather than use lower-level tools directly.

The preference `tools.disabled.global` holds a newline-separated
blocklist applied to all agents on top of the role-based set.

## Sub-Agent Job Lifecycle

The main agent delegates work by creating or updating todos and assigning
them to sub-agents. `AutonomousCoordinator` selects workers and submits
work through `SubAgentJobScheduler` under the configured concurrency limit.
When a worker completes, its result is persisted in the conversation history
and the UI is notified. There are no direct `agent:start` or
`agent:message` tools in the registered tool inventory.

## Autonomous Loop

`orchestration/AutonomousCoordinator` is constructed by `AgentManager`
in its `init { ... }` block and is reachable only through
`AgentManagerAutonomyOps`. It uses
`orchestration/AutonomousTaskPlanner` (todo expansion + worker
selection) and `orchestration/UxSeedTasks.all()` as the default UX
backlog. The per-job retry loop is bounded by
`agent.config.maxRetries`; the result of each worker call is sent
to the main LLM via `taskPlanner.reviewWorkerResult`, which expects
`APPROVED` or `RETRY` as the first line of the response.

The autonomous loop runs while there are pending or in-progress todos
and at least one busy sub-agent; otherwise it exits cleanly. The
public entry points are `assignNextTodo`, `assignTodoToAgent`,
`assignAllPendingTodos`, `seedUxTodos`, `startAutonomousProcessing`
(seeded with `UxSeedTasks.all()`), and `startAutonomousMode(goal)`.

## Templates

`AgentConfig.TEMPLATES` holds the default templates:

- `researcher`: read-only, broad tool set, default `maxTurns = 4`.
- `coder`: write/edit/terminal, default `maxTurns = 8`.
- `documenter`: documentation focus, default `maxTurns = 4`.
- `reviewer`: review focus, default `maxTurns = 4`.
- `tester`: test focus, default `maxTurns = 4`.

Each template fills in `provider`, `model`, `variant`,
`temperature`, `topP`, `maxTokens`, and `options` defaults.
`AgentConfig.fromTemplate(name)` returns a `SubAgent` configuration
with those defaults and `status = IDLE`.

## Persistence

State is persisted via typed Spring Data JPA stores over SQLite
tables:

- `sub_agents`: per-agent identity, status, current task, parent
  agent id, and JSON-encoded configuration.
- `sub_agent_configs`: per-agent allowed tool sets and related
  configuration payloads.
- `todos`: persisted todo items used by the autonomous loop and the
  main-agent todo summary.
- `conversation_history`: tool-call entries and per-agent message
  history.

UI and runtime reload from the database after restart.

## In-Flight Indicator

The `SubAgentsPanel` calls `inFlight.markAgentStart(agent.id)` when a
job is enqueued and `inFlight.markAgentEnd(agent.id)` when the
coroutine completes. The header `InFlightIndicator` shows pulsing
dots for the duration of the run.

## Remaining Work

- Further modularization of `agent/AgentManager` (file currently
  exceeds the 300 LOC target).
- Expanded integration tests for concurrent multi-agent execution
  paths.
- Improved UI polish for the SubAgents panel (cards, quick actions,
  logs preview, creation wizard) tracked in #26.
