# UC-0000056: Decompose Complex Todo

## Goal

Detect complex todos and split them into smaller actionable subtasks through an analysis sub-agent.

## Primary Actor

Autonomous runtime.

## Preconditions

- At least one pending todo exists.
- The todo is complex according to configured heuristics.
- A provider/model is available for analysis.

## Main Flow

1. The planner scans pending todos.
2. It selects a complex candidate.
3. An analyst agent is found or created.
4. The analyst returns concise subtasks.
5. The original todo is cancelled and subtasks are added.

## Result

Large tasks become smaller units that can be assigned to workers.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner.expandComplexTodoIfNeeded`
- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner.isComplex`

## Acceptance Criteria

- Empty decomposition results do not modify the original todo.
- Duplicate subtasks are removed.
- The original complex todo is cancelled only after subtasks are produced.
