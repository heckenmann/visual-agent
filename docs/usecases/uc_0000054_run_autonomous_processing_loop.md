# UC-0000054: Run Autonomous Processing Loop

## Goal

Run background processing that decomposes complex todos, assigns work, and stops when no pending or active work remains.

## Primary Actor

Desktop user or main orchestration agent.

## Preconditions

- Agent manager is initialized.
- Todo and sub-agent stores are available.

## Main Flow

1. Autonomous processing starts, optionally seeding UX todos.
2. The task planner decomposes complex pending todos when needed.
3. Pending todos are assigned up to worker and parallelism capacity.
4. Workers execute and report results.
5. The loop exits when there are no pending, in-progress, or busy-agent tasks.

## Result

The application can process a backlog without manual assignment for every task.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator.startAutonomousProcessing`
- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner`
- `de.heckenmann.visualagent.agent.AgentManager.startAutonomousProcessing`

## Acceptance Criteria

- Parallelism respects configured limits.
- The loop terminates when work is complete.
- Worker failures retry or cancel according to policy.
