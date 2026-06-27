# UC-0000050: View Agent Logs

## Goal

Let the user inspect a sub-agent's recent chat and execution log messages.

## Primary Actor

Desktop user.

## Preconditions

- At least one sub-agent exists.
- The sub-agent card exposes a logs action.

## Main Flow

1. The user opens logs for a sub-agent.
2. The UI collects agent history and current runtime metadata.
3. A modal log dialog displays the information.
4. The user closes the dialog without mutating agent state.

## Result

Sub-agent behavior can be inspected without switching to raw database or log files.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- Opening logs does not modify agent configuration.
- Empty history is handled gracefully.
- The dialog is readable at default window sizes.
