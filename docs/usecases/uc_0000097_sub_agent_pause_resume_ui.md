# UC-0000097: Pause and Resume Sub-Agent Execution in the Desktop UI

## Goal

Allow the user to pause or resume all autonomous sub-agent work, or one selected sub-agent, without cancelling in-flight work.

## Primary Actor

Desktop user.

## Preconditions

- The Sub-agents panel is open.
- At least one sub-agent may be configured.

## Main Flow

1. The panel shows the persisted global execution state and a global pause/resume action.
2. The user pauses all sub-agents; active atomic LLM or tool work finishes, while the next execution boundary waits.
3. The user can pause one agent independently; other eligible agents continue when the global gate is running.
4. Resuming the global gate does not clear an individual pause, and resuming an individual agent does not bypass a global pause.
5. State changes are persisted and reflected immediately in every open panel.

## Result

Sub-agent todos, assignments, conversations, and progress remain intact while execution is paused and continue after the applicable gates are resumed. The main agent remains active throughout.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.SubAgentExecutionControl`
- `de.heckenmann.visualagent.ui.application.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.agents.SubAgentRow`

## Acceptance Criteria

- Global and individual controls are visible with accessible icon tooltips.
- Pausing never cancels or reconstructs an in-flight operation.
- Both gates must be running before a worker starts its next safe operation.
- Deleting an agent removes its individual persisted pause state.
