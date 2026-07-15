# UC-0000014: Assign Todos To Subagents

## Goal

Ensure every todo reaches a responsible sub-agent so autonomous execution can pick it up without a separate assignment step.

## Primary Actor

Main orchestration agent.

## Preconditions

- At least one sub-agent exists.
- The autonomous coordinator loop is running.

## Main Flow

1. The main agent inspects todo state and available sub-agents via `agent:list`.
2. It creates a new todo, either with an explicit `assignedAgentId` or without an assignment.
3. The autonomous coordinator detects the pending todo and starts it when an idle agent is available and parallelism allows.
   - If the todo already has a valid `assignedAgentId`, that agent is used.
   - If the todo has no assignment, the coordinator auto-assigns the first idle sub-agent.
4. Sub-agent execution updates status and produces results.
5. Start, completion, and cancellation messages are persisted to the conversation.

## Result

Todo work is delegated while keeping the main agent as an orchestration layer that only manages sub-agents and todos. Todos created without an explicit assignment are still executed autonomously.

## Tool Calls

- `todos` (`add`): create a todo; `assignedAgentId` is optional. When provided it must reference an existing sub-agent.
- `agent:list`: list available sub-agents.

## Code Entry Points

- `de.heckenmann.visualagent.todo.TodoManager.add`
- `de.heckenmann.visualagent.agent.tools.TodosTool`
- `de.heckenmann.visualagent.agent.tools.AgentListTool`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator.assignAndReturnIfReady`

## Acceptance Criteria

- New todos may omit `assignedAgentId`; they are auto-assigned to an idle sub-agent by the coordinator.
- New todos with an explicit `assignedAgentId` must reference an existing sub-agent; invalid references are rejected.
- The autonomous coordinator selects the first pending todo ordered by `position`.
- Sub-agent results are surfaced through persisted conversation messages.
