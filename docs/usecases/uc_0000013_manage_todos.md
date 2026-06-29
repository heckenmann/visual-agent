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
2. The user creates a todo with a priority selected from the priority dropdown.
3. The user filters the visible list by status when they need a narrower view.
4. The user changes status from the row status dropdown or opens the edit dialog to update description, priority, and status together.
5. For delete actions, the UI shows an internal confirmation modal before removing the todo.
6. The todo manager records the change.
7. The todo store persists the authoritative state.
8. Agent prompts and tools read current todo summaries from persistence.

## Result

Todos stay synchronized between UI, database, and agent context.

## Tool Calls

- `todos`: manage persisted todo state when the action is initiated by a model call.

## Code Entry Points

- `de.heckenmann.visualagent.todo.TodoManager`
- `de.heckenmann.visualagent.ui.compose.TodoPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeModalHost`
- `de.heckenmann.visualagent.agent.tools.TodosTool`
- `de.heckenmann.visualagent.knowledge.TodoStore`

## Acceptance Criteria

- Todo changes survive restart.
- Main-agent context includes authoritative todo counters.
- UI and tool calls reflect the same persisted state.
- UI delete actions require internal modal confirmation.
- Priority and status are edited through bounded dropdown choices, not free text.
