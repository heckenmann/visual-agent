# UC-0000078: Cancel Sub-Agent Job

## Goal

Allow the user to stop a running queued sub-agent job without changing the assigned todo state.

## Primary Actor

Desktop user.

## Preconditions

- The sub-agents management panel is visible.
- A sub-agent has been started with the play button and a job is running or queued.

## Main Flow

1. The user clicks the run button on a sub-agent row.
2. The panel enqueues a job via [AgentManager.enqueueAgentJob] and stores the returned job id.
3. A stop button appears next to the run button.
4. The user clicks the stop button.
5. The panel calls [AgentManager.cancelSubAgentJob] with the stored job id.
6. [SubAgentJobScheduler] cancels the underlying coroutine and removes it from the registry.
7. The job finishes with a cancellation exception; the status line shows that the job was stopped.

## Result

The user can abort stuck or unnecessary sub-agent work while leaving the assigned todo open for retry.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentManager.cancelSubAgentJob`
- `de.heckenmann.visualagent.agent.SubAgentJobScheduler`
- `de.heckenmann.visualagent.ui.compose.ComposeSubAgentRow`
- `de.heckenmann.visualagent.ui.compose.SubAgentsPanel`

## Acceptance Criteria

- A stop button is visible on a sub-agent row while its job is active or queued.
- Clicking it stops that job and updates the active-job count.
- The assigned todo is not marked failed.
- `./gradlew ktlintCheck check test` passes.
- `jacocoTestCoverageVerification` (≥ 0.80 LINE) continues to pass.
