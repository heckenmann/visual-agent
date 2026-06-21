# UC-0000017: Queue Asynchronous Subagent Job

## Goal

Queue a sub-agent job asynchronously so the main agent can continue while the job runs later under the concurrency limit.

## Primary Actor

Main orchestration agent.

## Preconditions

- A target sub-agent exists or can be created.
- The tool input requests asynchronous execution.

## Main Flow

1. The main agent requests an async sub-agent job.
2. The scheduler records the job in a queue and immediately returns a job identifier.
3. When capacity becomes available, the job executes.
4. Completion emits a notification back into the main-agent flow.
5. Queue and active job counters are updated.

## Result

Long-running delegated work does not block the main agent.

## Tool Calls

- Any enabled tool can request asynchronous execution with `async=true`; sub-agent starts commonly use `agent:start`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.SubAgentJobScheduler`
- `de.heckenmann.visualagent.agent.AgentManager.enqueueAgentJob`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps`
- `de.heckenmann.visualagent.agent.tools.ToolRegistry`

## Acceptance Criteria

- Async tool calls return before job completion.
- Finished async jobs notify the main agent.
- Queue size and active job state are inspectable.
