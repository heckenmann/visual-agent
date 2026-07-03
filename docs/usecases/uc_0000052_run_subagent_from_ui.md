# UC-0000052: Run Subagent From UI

## Goal

Let the user manually start a sub-agent job from the sub-agent card.

## Primary Actor

Desktop user.

## Preconditions

- At least one sub-agent exists.
- The sub-agent panel is visible.

## Main Flow

1. The user enters task text in the sub-agent panel task field.
2. The user clicks the run action on a sub-agent card.
3. The panel sends the task text and selected agent ID to the agent manager.
4. The agent manager schedules execution for the selected sub-agent.
5. Status and active job count update in the UI.

## Result

The user can run targeted work on a specific sub-agent without waiting for autonomous todo assignment.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.compose.ActionIconButton`
- `de.heckenmann.visualagent.agent.AgentManager.runAgentJob`

## Acceptance Criteria

- The selected agent receives the requested work.
- The run action is disabled until task text is available.
- Runtime status changes are reflected in the card.
- Failures are surfaced to the conversation or UI.
