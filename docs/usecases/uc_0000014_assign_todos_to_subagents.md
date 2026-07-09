# UC-0000014: Assign Todos To Subagents

## Goal

Ensure every todo is created with a responsible sub-agent so autonomous execution can pick it up without a separate assignment step.

## Primary Actor

Main orchestration agent.

## Preconditions

- At least one sub-agent exists.
- The main agent references an existing sub-agent identifier.

## Main Flow

1. The main agent inspects todo state and available sub-agents via `agent:list`.
2. It creates a new todo with a mandatory `assignedAgentId` pointing to an existing sub-agent.
3. The autonomous coordinator detects the pending assigned todo and starts it when the agent is idle and parallelism allows.
4. Sub-agent execution updates status and produces results.
5. Start, completion, and cancellation messages are persisted to the conversation.

## Result

Todo work is delegated while keeping the main agent as an orchestration layer that only manages sub-agents and todos.

## Tool Calls

- `todos` (`add`): create a todo; requires `assignedAgentId`.
- `agent:list`: list available sub-agents.

## Code Entry Points

- `de.heckenmann.visualagent.todo.TodoManager.add`
- `de.heckenmann.visualagent.agent.tools.TodosTool`
- `de.heckenmann.visualagent.agent.tools.AgentListTool`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`

## Acceptance Criteria

- New todos must reference an existing sub-agent; invalid references are rejected.
- The autonomous coordinator selects the first pending todo ordered by `position`.
- Sub-agent results are surfaced through persisted conversation messages.
