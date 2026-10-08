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
2. After all attempt-owned tool calls finish, the planner sends the task description, final worker result, and bounded execution evidence to the main LLM via `reviewWorkerResult`. Scheduling acknowledgements are not proof of success.
3. The main LLM returns one JSON object with `verdict` (`APPROVED` or `RETRY`) and non-blank user-facing `feedback`. The same JSON Schema is included in the prompt and, when supported, sent as a native provider constraint.
4. Approved results complete the todo. The server persists the same review feedback as the main-agent response and publishes a conversation completion event; it does not invoke the model again to review approved work. The Conversation panel observes this event independently of suggestion settings and asynchronously reloads the latest history page after persistence.
5. Rejected results trigger retry until the retry limit is reached. The next worker instruction includes the rejected review's feedback and asks the worker to inspect existing work rather than repeat successful side effects.
6. Final rejection cancels the todo.

## Error Flow

- Invalid review JSON receives one immediate correction request for the same task and worker result. This does not rerun the worker or consume its business-retry count.
- If correction also fails, the todo is cancelled with `REVIEW_FAILED`, not `REVIEW_REJECTED`. A provider failure or incomplete review response also ends review with `REVIEW_FAILED` without rerunning worker actions.
- Cancellation stops review and correction; delayed approvals cannot complete a stopped todo.
- Only a valid `RETRY` judgment requests another worker execution.

## Provider Contract

- The shared `ChatRequestContext.responseSchema` carries the schema through provider routing and context budgeting. Native schema tokens count against the input window on every provider round.
- Codex app-server supports `outputSchema` on `turn/start`; the adapter uses that documented protocol field by default for schema requests.
- OpenAI-compatible and Ollama endpoints have unknown native support unless positively reported as `structured_outputs` or explicitly configured using the existing provider/model options: `structuredOutput.native=true`. This option is a user assertion about the endpoint and selected model, not a discovery result. Set it only after verifying support; Ollama Cloud currently does not support native structured output.
- `structuredOutput.native=false` explicitly disables native constraints for any adapter. Request options override model options, which override provider options. Unknown support uses the same JSON prompt and strict server validation, without unsupported API fields or model-name heuristics.
- OpenAI uses Spring AI's JSON-schema response format; Ollama uses Spring AI's object-valued `format`, including the tool-less path. No new HTTP client or dependency is required.
- JSON is never executed as a tool invocation. Only the server applies the validated verdict to todo lifecycle state.

## Result

Sub-agent work is reviewed by the main agent before being marked complete. Blank results are accepted when the main agent determines the work was accomplished through tool calls.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.orchestration.AutonomousTaskPlanner.reviewWorkerResult`
- `de.heckenmann.visualagent.orchestration.OrchestrationConstants.reviewPrompt`
- `de.heckenmann.visualagent.orchestration.evaluateWorkerResult`
- `de.heckenmann.visualagent.orchestration.WorkerReviewResult`
- `de.heckenmann.visualagent.agent.nativeResponseSchema`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator.processTodoWithLLM`
- `de.heckenmann.visualagent.ui.conversation.ConversationActivityHistoryEffect`

## Acceptance Criteria

- Only a complete, valid JSON result with verdict `APPROVED` approves the work.
- Retry limits are respected.
- Final rejection cancels the todo.
- Blank results are accepted when the main agent approves them.
- The approval request contains the task and worker result, not unrelated global todo/tool instructions.
- Missing or unknown fields, wrong types, invalid enum values, blank feedback, markdown fences, surrounding prose and verdict-only text are rejected. Historic messages are not rewritten.
- Worker streaming ends before the review starts. The todo remains `IN_PROGRESS` while its Todo panel, conversation card and overlay display `Reviewing result…`.
- A format error retries only evaluation once; it never causes automatic worker side effects to run again.
- Manual terminal transitions without a prior approval and failures retain their existing main-agent follow-up path.
- Reset invalidation prevents delayed approved feedback from restoring cleared history.
- Approved feedback becomes visible without another tool call or user action, even when the todo-status refresh completed before feedback was persisted. The completion refresh preserves older loaded history and live stream state, and never blocks the UI thread.
- Todo-status and completion refreshes share the existing latest-request generation guard, so a late older page cannot overwrite newer approved feedback.

## Library Decision

Existing Spring AI native schema options and Kotlin serialization cover this two-field contract. The generic Spring AI bean converter tolerates unknown properties and cleans response text by default, so strict Kotlin deserialization plus explicit field/type checks is used instead. No dependency is added. Reference: [Spring AI structured output](https://docs.spring.io/spring-ai/reference/api/structured-output.html), [Kotlin serialization](https://klibs.io/project/Kotlin/kotlinx.serialization), [Codex app-server](https://learn.chatgpt.com/docs/app-server), [Ollama structured output](https://docs.ollama.com/capabilities/structured-outputs).

## Related Issues

- #444: Reuse approved review feedback and distinguish worker streaming from review.
- #449, #455, #456: Wait for asynchronous work, pass correction feedback to retries, and review actual attempt-local tool evidence.
