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
2. The dialog displays identity, model, parameter, and option fields.
3. The user edits values.
4. Validation prevents invalid provider/model/option settings.
5. Saved changes update persisted sub-agent state.

## Result

Each sub-agent can use an appropriate role and model configuration.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.panels.AgentDetailsDialog`
- `de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport`
- `de.heckenmann.visualagent.agent.AgentManager.updateAgent`

## Acceptance Criteria

- Invalid dialog input disables save.
- Agent-specific model configuration is persisted.
- Existing values are preserved when fields are left unchanged.
