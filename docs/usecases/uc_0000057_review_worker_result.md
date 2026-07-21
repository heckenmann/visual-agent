# UC-0000057: Review Worker Result

## Goal

Let the main agent review a sub-agent's work result before a todo is completed. The main agent evaluates the result and decides whether to approve or request a retry.

## Primary Actor

Main orchestration agent.

## Preconditions

- A worker sub-agent has finished its LLM call (the `ToolCallingLoop` returned).
- The main provider can evaluate the result.

## Main Flow

1. The worker returns its result (may be blank if the work was done entirely through tool calls).
2. The planner sends the task description and result to the main LLM via `reviewWorkerResult`.
3. The main LLM responds with `APPROVED` or `RETRY`.
4. Approved results complete the todo.
5. Rejected results trigger retry until the retry limit is reached.
6. Final rejection cancels the todo.

## Result

Sub-agent work is reviewed by the main agent before being marked complete. Blank results are accepted when the main agent determines the work was accomplished through tool calls.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner.reviewWorkerResult`
- `de.heckenmann.visualagent.orchestration.OrchestrationConstants.reviewPrompt`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator.processTodoWithLLM`

## Acceptance Criteria

- Only responses starting with `APPROVED` approve the result.
- Retry limits are respected.
- Final rejection cancels the todo.
- Blank results are accepted when the main agent approves them.