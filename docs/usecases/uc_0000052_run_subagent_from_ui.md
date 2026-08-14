# UC-0000052: Run Subagent From UI

## Goal

Keep direct task entry out of the sub-agents panel; targeted work is started through persisted todos.

## Status

The former direct-run workflow is retired. The panel no longer exposes a task input or a direct run action.

## Primary Actor

Desktop user.

## Preconditions

- A todo can be assigned to a sub-agent.
- Todo execution controls are available.

## Main Flow

1. The user or main agent creates a todo and assigns it to a sub-agent.
2. The user or main agent starts the todo explicitly.
3. The autonomous coordinator schedules the assigned sub-agent.
4. Status and active job count update in the UI.

## Result

Targeted sub-agent work follows the same persisted todo workflow as autonomous execution.

## Tool Calls

- `todos`: create, assign, and explicitly start the todo.

## Code Entry Points

- `de.heckenmann.visualagent.ui.application.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.todo.TodoPanel`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`

## Acceptance Criteria

- The panel contains no direct task input or run action.
- Assigned work starts only through an explicit todo start action.
- Runtime status changes are reflected in the card.
- Failures are surfaced to the conversation or UI.
