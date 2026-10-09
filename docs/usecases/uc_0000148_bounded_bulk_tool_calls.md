# UC-0000148 — Bounded independent bulk tool calls

## Goal

Run independent enabled tools with lower latency while keeping authorization, cancellation,
resource limits, declaration order and non-atomic mutation semantics on the server.

## Actors

- Main agent or an explicitly configured sub-agent.
- A sandboxed JavaScript script using the same role-scoped tool allowlist.

## Preconditions

Each child has a unique non-blank opaque ID, an enabled canonical tool identity and JSON-object
arguments. A batch has at most 32 items. Recursive JavaScript/batch/help composition and detached
`async` children are rejected. All entries and aggregate argument limits are validated before any
child starts. Tools may reduce their timeout; they cannot extend the inherited outer deadline.

## Main Flow

1. The `tools_batch` and `javascript_execute` descriptions instruct the model to prefer bundling
   independent enabled calls whenever possible, while keeping calls with result dependencies sequential.
   A model requests multiple native function calls, selects `tools_batch`, or a script calls
   `await tools.callMany([{id: "time", name: "system_time", arguments: {}}])`.
2. The native provider adapter resolves names against the actual request callbacks. Explicit
   batch inputs accept canonical tool IDs or registered function names; JavaScript uses the same
   function names as `tools.call`.
3. The shared Reactor executor validates the entire batch and schedules every child through
   `ToolRegistry` with immutable provider call ID, batch ID and declaration sequence metadata.
4. Consecutive explicitly reviewed `READ_ONLY_PARALLEL` tools may overlap within per-request
   and global resource permits. Default `SEQUENTIAL_ONLY` and `EXCLUSIVE` tools form barriers;
   no model or script can override safety metadata. Only the pure server time query is initially
   marked parallel-safe. No database or filesystem mutation is assumed conflict-free.
5. Each child emits its existing STARTED/FINISHED activity events. Results collect in declaration
   order even when completion order differs. Each model-facing result preserves the single-call
   `{toolId, success, data, error}` envelope; explicit batches add only the opaque `id`. Error code,
   message, remediation and retryability remain visible. Ordinary execution failure is data and
   does not roll back or retry successful siblings.
6. JavaScript receives a genuine Promise and ordered `{id, toolId, success, data, error}` items.
   Only the context-owner thread resolves guest callbacks; host workers queue immutable outcomes.

## Alternate and Failure Flows

- Malformed, unauthorized, recursive and over-budget batches fail before tool side effects.
- Invalid JSON is rejected before execution. A child reporting `INVALID_ARGUMENT` stops the batch
  immediately, including when an earlier sibling is still running. In-flight siblings are cancelled,
  queued siblings are skipped and already completed results are retained. JavaScript rejects the
  Promise with `TOOL_ARGUMENTS` without waiting for the remaining calls.
- Cancellation disposes queued and in-flight subscriptions; no new child is launched afterward.
- An inherited deadline includes queue/admission time and bounds every child.
- Result capacity is reserved across concurrent JavaScript batches. Content is bounded per item
  and as a batch, including JSON escaping and identity metadata; omitted content is not proof of
  complete artifact inspection. Every JavaScript child consumes the existing call allowance.
- A batch is non-atomic. Mutations completed before failure remain committed. Any retry is explicit.
- Single calls retain their existing contracts. `tools.call` remains fail-fast. Workspace helper
  calls are separate and are not parallelized.

## Architecture and Dependencies

Issue #289 originally proposed coroutines. The current server architecture from #375 requires
Reactor `Mono`/`Flux`; the executor uses existing Reactor composition without a new dependency.
Global resource admission is an explicit semaphore boundary on Reactor boundedElastic; this is
an actual tool-work limit, not a thread-count cap. Synchronous Spring `ChatModel` compatibility
loops await the publisher only on their documented bounded-elastic boundary. Native provider
identity mapping no longer depends on thread-local callback correlation.

Spring AI's default manager is retained only for callback implementations without the server
batch contract. Its history, round limit and return-direct semantics are preserved by the adapter.
Research: Spring AI tool-calling API and Reactor ordered bounded batch composition; no additional
Kotlin concurrency library is needed.

## Tool Calls

- `tools_batch`: independent calls with `id`, canonical `tool` and object `arguments`.
- Enabled child tools through `ToolRegistry`, with their ordinary authorization and events.
- `javascript_execute`, exposing `tools.callMany` only inside its hardened guest context.

## Code Entry Points

- `ToolBatchExecutor`, `ToolBatchRequest`, `ToolBatchLimits`, `ToolBatchTool`.
- `SpringAiToolCallbacksAdapter`, `NativeToolBatchRound`, `CorrelatedToolExecution`.
- `JavaScriptBatchBridge`, `JavaScriptToolBridge`, `GraalJavaScriptExecutionService`.

## Verification

Gated scheduler tests cover overlap/order, safety barriers, authorization, partial failure,
cancellation, deadlines and escaped output bounds. Actual Graal runtime tests cover Promise
behavior, guest-thread ownership, malformed batches, per-child call budgets and host isolation.
Provider tests cover streaming/non-streaming rounds and immutable call identities.

## Related

- Issue #289; UC-0000020; UC-0000104.

## Review Regression Guarantees

JavaScript helper discovery and delegation exclude `tools_batch` and `javascript_execute`, including
indirect `tool_help` calls. Scripts use `callMany` so each child consumes the script allowance.
Regression tests cover helper delegation in both Graal sandbox modes.

Manual Spring-bean smoke verification executes two real `system_time` children through the shared
batch executor and denies indirect helper batch dispatch in both Graal sandbox modes.

Cancellation returns promptly but synchronous work that ignores interruption retains its global
and request admission until its callable returns. The subscription and synchronous callable share
a small reference-counted lease; work cancelled before it starts cannot retain that lease.
Reactor `using` remains the subscription owner. Reactor's cancellation cleanup and reactor-pool
release APIs do not track an independently running synchronous callable, so no additional library
is introduced for this ownership bookkeeping (Reactor 3.8.7 API and reactor-pool researched).
Regression tests cover cancellation and timeout of a non-cooperative callable followed by a second request.
