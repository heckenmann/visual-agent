# UC-0000051: Configure Subagent Details

## Goal

Let the user edit sub-agent name, role, template, provider/model overrides, parameters, options, and tool configuration details.

## Primary Actor

Desktop user.

## Preconditions

- The sub-agents panel is visible.
- A sub-agent is selected or a create action is requested.

## Main Flow

1. The user opens the agent details dialog.
2. The dialog displays identity, template, provider/model override, parameter, option, runtime limit, and tool fields.
3. The user edits values.
4. Validation prevents invalid provider/model/option settings.
5. Saved changes update persisted sub-agent state.

## Result

Each sub-agent can use an appropriate role and model configuration.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeContentModal`
- `de.heckenmann.visualagent.ui.compose.ComposeModalHost`
- `de.heckenmann.visualagent.ui.compose.ActionIconButton`
- `de.heckenmann.visualagent.agent.AgentManager.updateAgent`
- `de.heckenmann.visualagent.agent.AgentConfig`

## Acceptance Criteria

- Invalid dialog input disables save.
- Agent-specific model configuration is persisted.
- Existing values are preserved when fields are left unchanged.
- Agent-specific tool selections are persisted with the sub-agent configuration.
