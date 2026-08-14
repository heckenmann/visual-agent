# UC-0000078: Cancel Sub-Agent Job

## Goal

Keep direct sub-agent job cancellation out of the management panel; unfinished work is stopped through todo controls.

## Status

The former direct-job workflow is retired. Sub-agent rows no longer expose a task-run or direct-job-stop action.

## Primary Actor

Desktop user.

## Preconditions

- The Todo panel is visible.
- A todo is pending or in progress.

## Main Flow

1. The user clicks the stop action for one todo or all unfinished todos.
2. The coordinator cancels the assigned worker cooperatively.
3. The todo is persisted as `CANCELLED` and can be started again later.

## Result

The user can stop unfinished sub-agent work while keeping completed todos unchanged.

## Tool Calls

- `todos`: stop one todo or all unfinished todos.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`
- `de.heckenmann.visualagent.ui.todo.TodoPanel`

## Acceptance Criteria

- Todo stop controls are visible for pending and in-progress work.
- Completed todos are not affected by stop-all actions.
- `./gradlew ktlintCheck check test` passes.
- `jacocoTestCoverageVerification` (≥ 0.80 LINE) continues to pass.
