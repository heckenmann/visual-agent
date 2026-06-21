# UC-0000052: Run Subagent From UI

## Goal

Let the user manually start a sub-agent job from the sub-agent card.

## Primary Actor

Desktop user.

## Preconditions

- At least one sub-agent exists.
- The sub-agent panel is visible.

## Main Flow

1. The user clicks the run action on a sub-agent card.
2. The main window wiring builds a task prompt or asks for task text.
3. The agent manager schedules execution for the selected sub-agent.
4. Status and active job count update in the UI.

## Result

The user can run targeted work on a specific sub-agent without waiting for autonomous todo assignment.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.panels.SubAgentCardView`
- `de.heckenmann.visualagent.ui.MainWindowSubAgentWiring`
- `de.heckenmann.visualagent.agent.AgentManager.runAgentJob`

## Acceptance Criteria

- The selected agent receives the requested work.
- Runtime status changes are reflected in the card.
- Failures are surfaced to the conversation or UI.
