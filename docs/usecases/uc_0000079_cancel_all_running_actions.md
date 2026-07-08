# UC-0000079: Cancel All Running Actions

## Goal

Allow the user to stop every in-flight activity at once from the header.

## Primary Actor

Desktop user.

## Preconditions

- At least one action is in flight: main-agent stream, sub-agent job, pending tool call, or settings refresh.

## Main Flow

1. The header in-flight indicator appears while any activity is active.
2. The indicator shows a small stop-all button.
3. The user clicks the stop-all button.
4. The header callback calls [AgentManager.cancelAllRunningActions].
5. [AgentManager] cancels every registered sub-agent job via [SubAgentJobScheduler.cancelAllJobs].
6. Any main-agent stream cancels because the active [CancellationToken] is cancelled by the user.
7. Pending tool calls and the settings refresh finish naturally; the in-flight state is updated by the existing event listeners.

## Result

The user can recover from a pile-up of parallel work with a single click.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentManager.cancelAllRunningActions`
- `de.heckenmann.visualagent.agent.SubAgentJobScheduler`
- `de.heckenmann.visualagent.ui.compose.ActivityIndicator`
- `de.heckenmann.visualagent.ui.compose.InFlightIndicator`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceHeader`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- A stop-all button is visible in the header while any action is in flight.
- Clicking it cancels all sub-agent jobs and any active main-agent stream.
- The in-flight indicator disappears once everything has ended.
- `./gradlew ktlintCheck check test` passes.
- `jacocoTestCoverageVerification` (≥ 0.80 LINE) continues to pass.
