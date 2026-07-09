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
3. The loop continuously picks the first pending assigned todo by `position` whose agent is idle.
4. The selected agent is marked busy, the todo moves to `IN_PROGRESS`, and a start message is persisted.
5. The scheduler enforces `maxParallelSubAgents`; excess work waits until a slot frees.
6. Worker results are reviewed; approved results complete the todo and rejected or failed results cancel it.
7. Completion and cancellation messages are persisted to the conversation.
8. The loop exits when there are no pending, in-progress, or busy-agent tasks.

## Result

The application can process a backlog without manual assignment for every task.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator.startAutonomousProcessing`
- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner`
- `de.heckenmann.visualagent.agent.AgentManager.startAutonomousProcessing`

## Acceptance Criteria

- Only todos with a valid `assignedAgentId` are picked up.
- Parallelism respects configured limits.
- The loop terminates when work is complete.
- Worker failures retry or cancel according to policy.
- Start, completion, and cancellation messages are persisted as conversation history.
