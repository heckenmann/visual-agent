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
5. The panel groups its global Pause/Resume and Create actions at the right edge, with Create last.
6. The global execution status is displayed on a separate line below the actions.

## Result

The user can see which agents are idle, busy, or running multiple jobs.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.application.SubAgentsPanel`
- `de.heckenmann.visualagent.agent.AgentManager.getActiveJobCount`

## Acceptance Criteria

- Agents with multiple concurrent jobs show the correct count.
- UI state is derived from manager/runtime state, not stale local counters.
- Pause/Resume and Create remain adjacent and right-aligned in both execution states and at narrow panel widths.
- Execution status never splits the action group; action dimensions and spacing match the Todo panel.
