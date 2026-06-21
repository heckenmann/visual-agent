# UC-0000057: Review Worker Result

## Goal

Let the main review path approve or reject a worker result before a todo is completed.

## Primary Actor

Autonomous runtime.

## Preconditions

- A worker has produced a result for a todo.
- The main provider can evaluate the result.

## Main Flow

1. The worker returns its result.
2. The planner sends todo details and result to the main provider.
3. The provider responds with `APPROVED` or `RETRY`.
4. Approved results complete the todo.
5. Rejected results trigger retry until the retry limit is reached.

## Result

Autonomous work is reviewed before being marked complete.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner.reviewWorkerResult`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`

## Acceptance Criteria

- Only responses starting with `APPROVED` approve the result.
- Retry limits are respected.
- Final rejection cancels the todo.
