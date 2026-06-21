# UC-0000068: Maintain Use Case Traceability

## Goal

Ensure every newly implemented user-visible function is documented as a traceable use case.

## Primary Actor

Developer.

## Preconditions

- A new user-visible function, button, command, tool action, workflow, or persisted behavior is being implemented.
- The documentation convention in `docs/conventions.md` applies.

## Main Flow

1. The developer implements the new function.
2. The developer creates or updates a use-case document under `docs/usecases/`.
3. The document explicitly names the relevant buttons, commands, or tool actions.
4. The implementing public API references the use-case document from KDoc when practical.
5. The build packages the use-case documents for model access through the `usecases` tool.

## Result

User-visible behavior stays discoverable by humans and by agents answering product-function questions.

## Tool Calls

- `usecases` verifies model-visible use-case documentation through `list`, `show`, and `search`.

## Code Entry Points

- `docs/conventions.md`
- `docs/usecases/`
- `build.gradle.kts`

## Acceptance Criteria

- Newly implemented functions are not left without a use-case document.
- Each button maps to a dedicated use case or is explicitly named in a shared use case.
- The packaged use-case catalog remains queryable by enabled agents.
