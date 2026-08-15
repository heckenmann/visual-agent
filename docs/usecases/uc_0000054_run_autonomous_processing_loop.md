# UC-0000054: Run Autonomous Processing Loop

## Goal

Run background processing that decomposes complex todos, assigns work, and stops when no pending or active work remains.

## Primary Actor

Desktop user or main orchestration agent.

## Preconditions

- Agent manager is initialized.
- Todo and sub-agent stores are available.

## Main Flow

1. The autonomous coordinator remains stopped when `AgentManager` and the Compose application initialize.
2. The main agent or desktop user explicitly starts one todo or all unfinished todos.
3. The task planner decomposes complex pending todos when needed.
4. The loop continuously picks the first pending todo by `position` whose agent is idle.
   - If the todo has no `assignedAgentId`, the coordinator assigns the first idle sub-agent before starting it.
5. The selected agent is marked busy, the todo moves to `IN_PROGRESS`, and a start message is persisted.
6. The scheduler enforces `maxParallelSubAgents`; excess work waits until a slot frees.
7. Worker results are reviewed; approved results complete the todo and rejected or failed results cancel it.
8. Completion and cancellation messages are persisted to the conversation.
9. The loop exits when there are no pending, in-progress, or busy-agent tasks.

## Result

The application can process a backlog without manual assignment for every task. Newly created todos are persisted and picked up automatically.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator.startAutonomousProcessing`
- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner`
- `de.heckenmann.visualagent.agent.AgentManager.startAutonomousProcessing`
- `de.heckenmann.visualagent.ui.todo.TodoPanel`

## Acceptance Criteria

- The autonomous loop is stopped when the Compose application launches.
- The main agent can start one todo or all unfinished todos through the `todos` tool.
- The Todo panel exposes the same start and stop actions.
- Newly created todos are persisted and visible to the loop.
- Todos with a valid `assignedAgentId` are picked up by that agent.
- Todos without an assignment are auto-assigned to an idle sub-agent.
- Parallelism respects configured limits.
- The loop terminates when work is complete.
- Worker failures retry or cancel according to policy.
- Start, completion, and cancellation messages are persisted as conversation history.
