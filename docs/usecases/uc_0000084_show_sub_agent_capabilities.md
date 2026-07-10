# UC-0000084: Show Sub-Agent Capabilities

## Goal

Let the main agent inspect a sub-agent's full capabilities (tools, model, current task, recent log) so it can decide whether that agent is suitable for a todo.

## Primary Actor

Main orchestrator agent.

## Preconditions

- The sub-agent exists in the agent store.
- `AgentToolConfigService` has resolved the agent's tool set.

## Main Flow

1. The main agent calls `agent:show` with the target agent id.
2. The tool loads the agent from `AgentManager`.
3. The tool resolves the agent's enabled tools, template/config id, model, current todo/task, and recent log entries.
4. The tool returns a structured text response containing all capability details.
5. The main agent uses this information to create or update a todo with a matching `assignedAgentId`.

## Result

The main agent can match todos to agent capabilities before assignment.

## Tool Calls

- `agent:show` — returns full details for one sub-agent.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.AgentShowTool`
- `de.heckenmann.visualagent.agent.AgentToolConfigService`
- `de.heckenmann.visualagent.agent.AgentConfig`

## Acceptance Criteria

- `agent:show` requires an `id` parameter.
- Unknown agents produce a `failure` result.
- The response includes: id, name, role, status, model, template/config, tool list, current todo/task, and recent log entries.
- The tool list is filtered by globally disabled tools.
