# UC-0000015: Create Update Delete Subagents

## Goal

Allow users and the main agent to manage sub-agent definitions, roles, templates, and persisted configuration.

## Primary Actor

Desktop user and main orchestration agent.

## Preconditions

- Sub-agent persistence is available.
- The caller has access to sub-agent lifecycle UI or tools.

## Main Flow

1. A user or tool requests create, update, or delete.
2. For user-triggered delete actions, the UI shows an internal confirmation modal before deletion.
3. The agent manager validates and applies the lifecycle change.
4. Sub-agent metadata and configuration are persisted.
5. UI cards refresh from the updated state.

## Result

The available sub-agent pool can be shaped for the current workspace needs.

## Tool Calls

- `agent:create`: create a sub-agent.
- `agent:update`: update sub-agent metadata/configuration.
- `agent:delete`: delete a sub-agent.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentManager`
- `de.heckenmann.visualagent.agent.tools.AgentCreateTool`
- `de.heckenmann.visualagent.agent.tools.AgentUpdateTool`
- `de.heckenmann.visualagent.agent.tools.AgentDeleteTool`
- `de.heckenmann.visualagent.ui.compose.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeModalHost`

## Acceptance Criteria

- If all sub-agents are deleted, startup can seed defaults when required by lifecycle policy.
- Deleted agents are removed from persisted tool configuration.
- UI reflects changes without restart.
- UI delete actions require internal modal confirmation.
