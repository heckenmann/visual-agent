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
2. The user creates a todo. New todos are appended at the end of the list.
3. The user filters the visible list by status when they need a narrower view.
4. The user drags a todo by its drag handle to reorder the list. The first pending todo is the next one to process.
5. The user changes status from the row status dropdown or opens the edit dialog to update description and status together.
6. For delete actions, the UI shows an internal confirmation modal before removing the todo.
7. The todo manager records the change.
8. The todo store persists the authoritative state ordered by `position`.
9. Agent prompts and tools read current todo summaries from persistence.

## Result

Todos stay synchronized between UI, database, and agent context.

## Tool Calls

- `todos`: manage persisted todo state when the action is initiated by a model call, including the `reorder` action.

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
- Todos can be reordered by dragging the row drag handle.
- The first pending todo is visually highlighted as the next task.
- Status is edited through a bounded dropdown choice, not free text.
- The `todos` tool supports a `reorder` action to change which task is next.
