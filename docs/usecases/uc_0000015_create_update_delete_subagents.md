# UC-0000015: Create Update Delete Subagents

## Goal

Allow users and the main agent to manage sub-agent definitions, roles, templates, and persisted configuration.

## Primary Actor

Desktop user and main orchestration agent.

## Preconditions

- Sub-agent persistence is available.
- The caller has access to sub-agent lifecycle UI or tools.

## Main Flow

1. A user or the main model requests create, update, or delete; application startup never creates sub-agents implicitly.
2. For user-created sub-agents, the UI opens a creation dialog that captures name, role, and a template selected from the template dropdown.
3. For updates, the details dialog exposes bounded dropdowns for template, provider override, and model override while leaving custom variant/options fields explicit.
4. For user-triggered delete actions, the UI shows an internal confirmation modal before deletion.
5. Before model-owned creation, the main model calls `agent:list` and reuses a suitable persisted agent when possible.
6. If none is suitable, the main model calls `agent:create` and uses the returned identifier for later todo assignment.
7. The agent manager validates and applies the lifecycle change.
8. Sub-agent metadata and configuration are persisted.
9. UI cards refresh from the updated state.

## Result

The available sub-agent pool can be shaped for the current workspace needs.

## Tool Calls

- `agent_create`: create a sub-agent.
- `agent_update`: update sub-agent metadata/configuration. Its optional `tools` array accepts the provider function names shown in agent inventories; the server validates them against registered tools and persists their internal IDs. Invalid names return a tool argument error without changing the agent.
- `agent_delete`: delete a sub-agent.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentManager`
- `de.heckenmann.visualagent.agent.tools.AgentCreateTool`
- `de.heckenmann.visualagent.agent.tools.AgentUpdateTool`
- `de.heckenmann.visualagent.agent.tools.AgentDeleteTool`
- `de.heckenmann.visualagent.ui.application.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.agents.SubAgentCreationForm`
- `de.heckenmann.visualagent.ui.modal.composeModalHost`
- `de.heckenmann.visualagent.ui.components.ActionIconButton`

## Acceptance Criteria

- Startup loads exactly the persisted records; an empty or partial inventory remains empty or partial.
- Deleted sub-agents are not recreated automatically.
- Model-owned creation occurs only through `agent:create` after `agent:list` inspection.
- Deleted agents are removed from persisted tool configuration.
- UI reflects changes without restart.
- The panel opens a form dialog for creating a sub-agent instead of rendering creation fields inline.
- UI delete actions require internal modal confirmation.
- Provider and model overrides are selected from the active provider catalog when possible.
