# UC-0000017: Queue Assigned Todo Work

## Goal

Keep assigned todo work pending while all sub-agent slots are occupied, then process it when a later autonomous polling pass finds capacity.

## Primary Actor

Autonomous coordinator.

## Preconditions

- A persisted todo is assigned to an existing sub-agent.
- The configured concurrency limit may already be reached.

## Main Flow

1. The autonomous coordinator identifies assigned pending work.
2. If every slot is occupied, the coordinator leaves the todo `PENDING` and returns.
3. A later polling pass selects the pending todo when capacity becomes available.
4. The selected sub-agent processes the todo.
5. Completion updates the persisted todo, conversation history, and UI state.

## Result

Assigned work remains pending until a worker is available, without exposing direct sub-agent execution tools to the main agent.

## Tool Calls

- `todos` with an assignment action associates work with a sub-agent.
- The autonomous coordinator performs scheduling; no direct `agent:start` or `agent:message` tool exists.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`
- `de.heckenmann.visualagent.agent.SubAgentJobScheduler`
- `de.heckenmann.visualagent.agent.AgentManager`

## Acceptance Criteria

- Assigned work remains `PENDING` when all workers are busy.
- A later autonomous polling pass retries pending assigned work when capacity is available.
- Completed work updates persisted todo and conversation state.
