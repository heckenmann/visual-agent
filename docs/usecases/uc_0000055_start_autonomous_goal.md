# UC-0000055: Start Autonomous Goal

## Goal

Let the user or main agent enqueue a top-level objective and run autonomous processing for that objective.

## Primary Actor

Desktop user or main orchestration agent.

## Preconditions

- The objective text is non-blank.
- Autonomous processing can access todo and agent state.

## Main Flow

1. A goal string is submitted.
2. The goal is added as a todo.
3. Autonomous processing starts without seeding unrelated defaults.
4. Workers process the goal through normal assignment and review.

## Result

A single user-defined objective can drive an autonomous work session.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator.startAutonomousMode`
- `de.heckenmann.visualagent.agent.AgentManager.startAutonomousMode`

## Acceptance Criteria

- Blank goals do not create junk todos.
- The new goal is persisted before processing.
- Default UX seed tasks are not added for this flow.
