# UC-0000018: Display Subagent Status And Jobs

## Goal

Show each sub-agent's status and active job count in the UI.

## Primary Actor

Desktop user.

## Preconditions

- Sub-agent panel is visible.
- Runtime job counters are available.

## Main Flow

1. The panel loads persisted sub-agent definitions.
2. Runtime status and active job counts are read from the agent manager.
3. Each agent card displays identifying metadata and current execution state.
4. Updates are applied when lifecycle or job events occur.

## Result

The user can see which agents are idle, busy, or running multiple jobs.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.panels.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.panels.SubAgentCardView`
- `de.heckenmann.visualagent.agent.AgentManager.getActiveJobCount`

## Acceptance Criteria

- Agents with multiple concurrent jobs show the correct count.
- UI state is derived from manager/runtime state, not stale local counters.
