# UC-0000054: Run Autonomous Processing Loop

## Goal

Run event-driven background processing that decomposes complex todos, assigns work, and waits for committed work or capacity signals.

## Primary Actor

Desktop user or main orchestration agent.

## Preconditions

- Agent manager is initialized.
- Todo and sub-agent stores are available.

## Main Flow

1. The autonomous coordinator remains stopped when `AgentManager` and the Compose application initialize.
2. The main agent or desktop user explicitly starts one todo or all unfinished todos.
3. The task planner decomposes complex pending todos when needed.
4. A conflated work signal makes the coordinator select pending todos by `position` while compatible agents are idle.
   - If the todo has no `assignedAgentId`, the coordinator assigns the first idle sub-agent before starting it.
5. The selected agent is marked busy, the todo moves to `IN_PROGRESS`, and a start message is persisted.
6. The scheduler enforces `maxParallelSubAgents`; excess work waits for a slot-release, resume, or parallelism-change signal.
7. Worker results are reviewed; approved results complete the todo and rejected or failed results cancel it.
8. Completion and cancellation messages are persisted before the terminal todo transition triggers a main-agent review. Terminal outcomes include a structured reason so execution failures are not confused with user cancellations.
9. The coordinator drains all immediately claimable work and then waits for the next conflated signal without polling.

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
- Newly created eligible todos wake the coordinator immediately after persistence.
- Todos with a valid `assignedAgentId` are picked up by that agent.
- Todos without an assignment are auto-assigned to an idle sub-agent.
- Parallelism respects configured limits.
- With no immediately executable work, the coordinator waits without polling for the next committed work or capacity signal.
- Worker failures retry or cancel according to policy.
- Start, completion, and cancellation messages are persisted as conversation history.
