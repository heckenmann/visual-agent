# UC-0000016: Process an Assigned Todo with a Sub-Agent

## Goal

Process a todo assigned to a sub-agent while respecting the configured maximum parallel sub-agent count.

## Primary Actor

Autonomous coordinator.

## Preconditions

- A persisted todo is assigned to an existing sub-agent.
- The maximum parallel sub-agent limit may already be reached.

## Main Flow

1. The autonomous coordinator selects an assigned pending todo.
2. The scheduler waits until capacity is available.
3. The selected sub-agent processes the todo.
4. Active job counters are updated while the job runs.
5. The result is persisted and the todo status is updated.
6. The conversation history and UI reflect the completed work.

## Result

Assigned work is completed without exceeding configured concurrency and its result remains visible to the user.

## Tool Calls

- `todos` with an assignment action associates a todo with a sub-agent.
- The autonomous coordinator schedules the resulting work; no direct `agent:start` or `agent:message` tool exists.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`
- `de.heckenmann.visualagent.agent.SubAgentJobScheduler`
- `de.heckenmann.visualagent.agent.AgentManager`

## Acceptance Criteria

- Assigned todos wait instead of failing when the concurrency limit is reached.
- Active job counts are incremented and decremented reliably.
- Completed work updates persisted todo and conversation state.
