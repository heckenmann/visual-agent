# UC-0000057: Review Worker Result

## Goal

Automatically complete a todo when the sub-agent's LLM call finishes without error.

## Primary Actor

Autonomous runtime.

## Preconditions

- A worker sub-agent has finished its LLM call (the `ToolCallingLoop` returned).
- The call did not throw an exception.

## Main Flow

1. The sub-agent's LLM call completes — the model returned a final response with no more tool calls, or the tool-calling loop exhausted its rounds.
2. The autonomous coordinator receives the result (which may be blank if the sub-agent accomplished everything through tool calls without a final text summary).
3. The todo is automatically marked as `COMPLETED`.
4. A `sub_agent` notification message is persisted to the conversation history.
5. The main agent is triggered to review the change and inform the user.

## Result

Every sub-agent LLM call that finishes without error automatically completes its assigned todo. No explicit `todos complete` call from the sub-agent is required.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner.reviewWorkerResult`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator.processTodoWithLLM`

## Acceptance Criteria

- A sub-agent LLM call that returns without error always completes the todo.
- Blank results (tool-only work) are accepted as successful.
- Exceptions still trigger the retry loop and eventual cancellation.
