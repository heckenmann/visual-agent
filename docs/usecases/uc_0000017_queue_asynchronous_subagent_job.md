# UC-0000017: Queue Assigned Todo Work

## Goal

Queue assigned todo work so the autonomous coordinator can continue planning while sub-agents process work under the configured concurrency limit.

## Primary Actor

Autonomous coordinator.

## Preconditions

- A persisted todo is assigned to an existing sub-agent.
- The configured concurrency limit may already be reached.

## Main Flow

1. The autonomous coordinator identifies assigned pending work.
2. The scheduler records work that cannot start immediately.
3. When capacity becomes available, the selected sub-agent processes the todo.
4. Completion updates the persisted todo, conversation history, and UI state.
5. Queue and active-job counters reflect current work.

## Result

Long-running delegated work is queued safely without exposing direct sub-agent execution tools to the main agent.

## Tool Calls

- `todos` with an assignment action associates work with a sub-agent.
- The autonomous coordinator performs scheduling; no direct `agent:start` or `agent:message` tool exists.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`
- `de.heckenmann.visualagent.agent.SubAgentJobScheduler`
- `de.heckenmann.visualagent.agent.AgentManager`

## Acceptance Criteria

- Assigned work is queued when all workers are busy.
- Completed work updates persisted todo and conversation state.
- Queue size and active-job state remain inspectable.
