# UC-0000083: Query Todo Result

## Goal

Let the main agent retrieve the stored result summary for a completed or processed todo.

## Primary Actor

Main orchestration agent.

## Preconditions

- The todo exists.
- Structured knowledge was saved for the todo during sub-agent execution.

## Main Flow

1. The main agent calls the `todos` tool with action `get-result` and the todo `id`.
2. The tool searches the knowledge store for the most recent record tagged with `todo:<id>`.
3. The stored summary is returned, or a clear "no result available" message when nothing was saved.

## Result

The agent can report concrete outcomes without re-executing the task.

## Tool Calls

- `todos` (`get-result`): retrieve the result summary for a todo.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.TodosTool`
- `de.heckenmann.visualagent.knowledge.MemoryStore.searchMemories`

## Acceptance Criteria

- A missing or unprocessed todo yields a user-friendly "no result available" response.
- The returned summary is extracted from structured knowledge when possible.
- Raw memory content is truncated to a reasonable length when no structured summary is found.

## Task-contract integration

Optional persisted task contracts, capability routing, scoped execution and evidence-backed acceptance are specified in [UC-0000149](uc_0000149_execute_task_contracts.md). Legacy description-only tasks retain their existing behavior. Contracted tasks carry the immutable revision through worker/reviewer requests and durable attempt records; editing scope invalidates stale completion. Existing Todo/Conversation details distinguish attempts, unchecked evidence and accepted output. No implicit agent creation or dependency scheduling is added.
