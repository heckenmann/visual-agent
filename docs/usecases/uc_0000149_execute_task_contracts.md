# UC-0000149: Execute and review contracted agent tasks

## Goal
Delegate bounded work with explicit deliverables, measurable acceptance criteria and durable evidence, using the existing todo lifecycle.

## Actors
User, main agent, persisted worker, optional independently configured reviewer.

## Preconditions
A persisted agent exists. Its actual registered and enabled tools satisfy the required operations. No agent is created implicitly.

## Flow
1. The main agent or user attaches a validated version-1 contract to a todo. Server revisions are monotonic. Legacy todos retain the existing text workflow.
2. The coordinator claims an immutable snapshot and intersects task restrictions with current agent tools, global toggles and existing resource grants. Null restrictions inherit; empty restrictions deny.
3. The worker receives versioned role instructions, selected input references and the contract. Unrelated agent history is excluded by default. Function schemas provide the authoritative enabled function names.
4. Tool access is checked again at execution time. Direct calls, JavaScript workspace helpers, script loading, nested tools, batches and detached calls inherit the same trusted authority. Workspace-scoped tasks cannot use an opaque file ID, external grant or unbounded download destination.
5. The worker returns a strict worker-only JSON handoff. Server-assigned todo, agent, execution and attempt identities cannot be set by worker JSON. Each attempt is saved durably before review; tool observations exclude raw outputs.
6. The server reads current artifact bytes (maximum 1 MiB per artifact), captures digest/MIME/size and evaluates existence, expected text, JSON validity or a quote against a captured source file. A digest proves identity, not correctness.
7. A separate reviewer request sees bounded actual artifact and input content with no tools or agent history. An explicitly configured reviewer must exist and differ from the worker; its model overrides are honored. Missing or unsupported evidence is NOT_CHECKED.
8. Completion requires all required checks to pass, a PRODUCED outcome without blockers, unchanged artifact versions and an unchanged todo snapshot. The accepted result and completion state are committed together. Attempts remain separate from the accepted result.
9. Review feedback corrects the same task on retry. Unsupported required checks and explicit blockers stop for intervention. Contract edits, cancellation, removal or reassignment revoke the old execution and prevent stale completion.
10. Verification criteria can require a successful tool observation via `executionTool`; worker prose never establishes execution. Unreadable or truncated manual evidence remains NOT_CHECKED. Source-quote predicates are rechecked immediately before acceptance.
11. The user can edit objective, inputs, outputs, criteria, constraints, stop conditions and workspace paths, and inspect attempts and observations in the todo response overlay.

## Decomposition
A bounded structured proposal maps existing parent criterion IDs onto two to eight children. The server inherits policy, inputs, constraints and stop conditions. It rejects missing coverage, unknown IDs or uncovered artifacts, keeping the original task executable on malformed proposals. Dependency scheduling remains outside this use case.

## Tool Calls

- `todos`: add/update with optional contract; read `get-contract`, `get-attempts` (one bounded record per page, offset and nextOffset), and legacy summary `get-result`.
- `workspace_file`: only enabled actions and scoped normalized workspace paths.
- `javascript_execute` and `tools_batch`: inherited task authority for nested operations.

## Code Entry Points
- `TaskContractCodec`, `TaskDecomposition`, `WorkerHandoff`
- `TodoManager`, `R2dbcTodoStore`, Flyway `V6__task_contracts_and_attempts.sql`
- `AutonomousTaskPlanner`, `AutonomousTodoProcessor`, `TaskExecutionService`, `TaskArtifactVerifier`
- `TaskToolAccess`, `ToolRegistry`, `JavaScriptToolBridge`
- `SpringTodoPort`, `TodoContractFields`, `TodoTaskDetails`

Description-only tasks remain one executable job regardless of language or length. Automatic contract decomposition requires multiple criteria and outputs, complete non-overlapping coverage, and an explicit independent-package proposal. Missing or dependent plans preserve the original task.

## Validation
The versioned offline corpus uses the production coordinator, real H2 persistence, registry scope and workspace byte verifier. Additional tests cover revision conflicts, handoff forgery, missing decomposition coverage, resource traversal and global/ownership revocation. Scripted reviewers test server behavior; their results make no claim about real model quality. Opt-in real-model evaluation is documented in `docs/task-evaluations.md`.

## Library decision
Reuse the existing Apache-2.0 `kotlinx.serialization` dependency for strict typed parsing and schema-compatible DTOs. Primary source: https://github.com/Kotlin/kotlinx.serialization (also indexed on https://klibs.io/). A general JSON Schema validator or schema generator adds dependencies without improving the small, explicitly validated contract boundary.

## Related issues
#464, #465, #466, #467, #468, #469, #470. Existing title, deadline, multi-assignment, dependency, execution budget and context features retain their own issue scope.
