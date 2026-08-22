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
3. The user sees all todos in their persisted order; the panel does not filter them by status.
4. The user drags a todo by its drag handle to reorder the list. The first pending todo is the next one to process.
5. The user starts all unfinished todos or stops all pending and in-progress todos with the panel actions.
6. The user starts or stops an individual todo from its row. Starting a cancelled todo resets it to `PENDING`; stopping a todo changes it to `CANCELLED` and cancels its worker cooperatively.
7. The user changes status from the edit dialog to update description and status together.
8. For delete actions, the UI shows an internal confirmation modal before removing the todo.
9. The todo manager records the change.
10. The todo store persists the authoritative state ordered by `position`.
11. Agent prompts and tools read current todo summaries from persistence.
12. When an autonomous todo reaches `COMPLETED` or `CANCELLED`, the main agent receives a request-local user instruction to review the persisted status notification.
13. The main agent removes a terminal todo once its history and result are no longer needed, or after its result has been incorporated into the final answer, unless the user requested that it be retained.

## Result

Todos stay synchronized between UI, database, and agent context.

## Tool Calls

- `todos`: manage persisted todo state when the action is initiated by a model call, including the `reorder` action.

## Code Entry Points

- `de.heckenmann.visualagent.todo.TodoManager`
- `de.heckenmann.visualagent.ui.todo.TodoPanel`
- `de.heckenmann.visualagent.ui.modal.ComposeModalHost`
- `de.heckenmann.visualagent.agent.tools.TodosTool`
- `de.heckenmann.visualagent.knowledge.TodoStore`

## Acceptance Criteria

- Todo changes survive restart.
- Main-agent context includes authoritative todo counters.
- UI and tool calls reflect the same persisted state.
- UI delete actions require internal modal confirmation.
- The panel provides start-all and stop-all controls for unfinished todos.
- Each todo row provides start and stop controls with status-appropriate enablement.
- An in-progress todo row expands with an animated one-line LLM response preview. Supported streaming models move newer text in from the right and older text out to the left; providers without streaming support use the complete-response fallback.
- Start and stop actions never change or restart todos that are already `COMPLETED`.
- Todos can be reordered by dragging the row drag handle.
- The first pending todo is visually highlighted as the next task.
- Status is edited in the todo editor through a bounded dropdown choice, not free text.
- The `todos` tool supports a `reorder` action to change which task is next.
- Sub-agents can read todo state and stored results, but only the main agent and orchestrator can change todo lifecycle state.
- Autonomous terminal-status reviews always end with an explicit user instruction accepted by every configured provider.
- Terminal todos are cleaned up after their history and result are no longer needed or have been incorporated into the final answer, unless they remain useful for follow-up, reporting, or a user-requested record.
