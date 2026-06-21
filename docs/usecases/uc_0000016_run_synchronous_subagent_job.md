# UC-0000016: Run Synchronous Subagent Job

## Goal

Run a sub-agent job synchronously so the caller waits for the result while respecting the configured maximum parallel sub-agent count.

## Primary Actor

Main orchestration agent.

## Preconditions

- A target sub-agent exists or can be created.
- The main agent has sub-agent execution tools.
- The maximum parallel sub-agent limit may already be reached.

## Main Flow

1. The main agent requests a synchronous sub-agent job.
2. The scheduler waits until capacity is available.
3. The job runs on the selected or newly created sub-agent.
4. Active job counters are updated while the job runs.
5. The caller receives the final result.

## Result

The main agent gets a completed sub-agent result without exceeding configured concurrency.

## Tool Calls

- `agent:start`: start a synchronous sub-agent job.
- `agent:message`: send a message to an existing sub-agent.

## Code Entry Points

- `de.heckenmann.visualagent.agent.SubAgentJobScheduler`
- `de.heckenmann.visualagent.agent.AgentManager.runAgentJob`
- `de.heckenmann.visualagent.agent.AgentManager.startAgentJob`
- `de.heckenmann.visualagent.agent.tools.AgentStartTool`
- `de.heckenmann.visualagent.agent.tools.AgentMessageTool`

## Acceptance Criteria

- Synchronous requests wait instead of failing when the limit is reached.
- Active job counts are incremented and decremented reliably.
