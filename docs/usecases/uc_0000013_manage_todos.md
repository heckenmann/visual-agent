# UC-0000013: Manage Todos

## Goal

Let users create, update, complete, delete, and inspect task todos that are also available to the agent context.

## Primary Actor

Desktop user.

## Preconditions

- Todo persistence is available.
- Todo panel or todo tools are enabled.

## Main Flow

1. The user opens the todo panel.
2. The user creates or edits a todo with priority/status metadata.
3. The todo manager records the change.
4. The todo store persists the authoritative state.
5. Agent prompts and tools read current todo summaries from persistence.

## Result

Todos stay synchronized between UI, database, and agent context.

## Tool Calls

- `todos`: manage persisted todo state when the action is initiated by a model call.

## Code Entry Points

- `de.heckenmann.visualagent.todo.TodoManager`
- `de.heckenmann.visualagent.ui.panels.TodoPanel`
- `de.heckenmann.visualagent.agent.tools.TodosTool`
- `de.heckenmann.visualagent.knowledge.TodoStore`

## Acceptance Criteria

- Todo changes survive restart.
- Main-agent context includes authoritative todo counters.
- UI and tool calls reflect the same persisted state.
