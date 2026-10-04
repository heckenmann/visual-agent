# UC-0000017: Queue Assigned Todo Work

## Goal

Keep assigned todo work pending while all sub-agent slots are occupied, then process it when an event signals newly available capacity.

## Primary Actor

Autonomous coordinator.

## Preconditions

- A persisted todo is assigned to an existing sub-agent.
- The configured concurrency limit may already be reached.

## Main Flow

1. The autonomous coordinator identifies assigned pending work.
2. If every slot is occupied, the coordinator leaves the todo `PENDING` and returns.
3. Slot release, resume, or a parallelism increase signals the coordinator, which immediately selects pending work.
4. The selected sub-agent processes the todo.
5. Completion updates the persisted todo, conversation history, and UI state.

## Result

Assigned work remains pending until a worker is available, without exposing direct sub-agent execution tools to the main agent.

## Tool Calls

- `todos` with an assignment action associates work with a sub-agent.
- The autonomous coordinator performs scheduling; no direct `agent:start` or `agent:message` tool exists.

## Background Job Lifecycle

The scheduler's internal background-job API registers each job before admitting its work.
Immediate success, failure, and cancellation must not leave completed jobs in the registry.
Cancellation before dispatch or with an already cancelled parent still reports one terminal
result, without executing the requested work. Closing the scheduler cancels both running and
queued background jobs and releases their capacity.

The implementation uses the existing coroutine library's atomic start with a registration
barrier. Completion handlers only remove registry entries; completion callbacks remain in
the job body because they can perform persistence and must not block coroutine completion
handlers. This lifecycle guarantee addresses issue #437 and does not introduce a new tool.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`
- `de.heckenmann.visualagent.agent.SubAgentJobScheduler`
- `de.heckenmann.visualagent.agent.AgentManager`

## Acceptance Criteria

- Assigned work remains `PENDING` when all workers are busy.
- A capacity-change signal retries pending assigned work without normal-path polling.
- Completed work updates persisted todo and conversation state.
- Background jobs report exactly one terminal result and are no longer cancellable after completion.
