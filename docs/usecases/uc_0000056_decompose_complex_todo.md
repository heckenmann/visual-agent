# UC-0000056: Decompose Complex Todo

## Goal

Detect complex todos and split them into smaller actionable subtasks through an analysis sub-agent.

## Primary Actor

Autonomous runtime.

## Preconditions

- At least one pending todo exists.
- The todo is complex according to configured heuristics.
- A persisted analysis sub-agent and provider/model are available for analysis.

## Main Flow

1. The planner scans pending todos.
2. It selects a complex candidate.
3. An existing persisted analyst agent is selected.
4. The analyst returns concise subtasks.
5. One database transaction checks the original pending snapshot, cancels it, and appends all subtasks. Any concurrent edit, claim, cancellation, or deletion rejects the stale result without creating children.

## Result

Large tasks become smaller units that can be assigned to workers.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner.expandComplexTodoIfNeeded`
- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner.isComplex`

## Acceptance Criteria

- Empty decomposition results do not modify the original todo.
- If no analyst exists, the planner creates no implicit agent and makes the todo available for direct execution by an existing worker.
- Duplicate subtasks are removed.
- The original complex todo is cancelled only after subtasks are produced.
- Subtasks are appended after the decomposed todo's position; the user or model can reorder them afterwards.
- An identical single-child result does not replace the parent. Generated children persist a leaf-generation marker and are not recursively decomposed again.
- Removing the analyst or invalidating the parent stops queued/running analysis. Analysis-agent deletion cannot resurrect that agent through late cleanup.

## Implementation Decision

Issues #453 and #454 reuse Spring Data R2DBC transactions and the existing coroutine scheduler. The database serializes todo mutations through a guard row; no new dependency or in-memory lifecycle cache is introduced. Decomposition generation is persisted so restart cannot reset the recursion bound.
