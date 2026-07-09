# UC-0000014: Assign Todos To Subagents

## Goal

Let the main agent assign pending todos to sub-agents for autonomous execution.

## Primary Actor

Main orchestration agent.

## Preconditions

- At least one sub-agent exists or can be created.
- Pending todos exist.
- Agent assignment tools are enabled for the main agent.

## Main Flow

1. The main agent inspects todo state.
2. It selects an existing sub-agent or creates one for the work.
3. It assigns one or more todos to sub-agent jobs, starting with the topmost pending todo by `position`.
4. Sub-agent execution updates status and produces results.
5. Completion is reported back to the main agent.

## Result

Todo work is delegated while keeping the main agent as orchestration layer.

## Tool Calls

- `agent:assign-todo`: assign a specific todo to a sub-agent.
- `agent:assign-next-todo`: assign the next eligible todo.
- `agent:assign-all-todos`: assign all eligible todos.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.AgentAssignTodoTool`
- `de.heckenmann.visualagent.agent.tools.AgentAssignNextTodoTool`
- `de.heckenmann.visualagent.agent.tools.AgentAssignAllTodosTool`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`

## Acceptance Criteria

- Assignments use persisted todo state.
- `agent:assign-next-todo` selects the first pending todo ordered by `position`.
- Sub-agent results are surfaced through conversation/tool events.
