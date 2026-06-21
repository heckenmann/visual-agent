# Development Conventions

## Use-Case Traceability

Every newly implemented user-visible function must have a matching use-case document under `docs/usecases/`.

This includes:
- toolbar buttons
- panel buttons
- menu and command-palette actions
- tool-call actions
- autonomous workflows
- persisted behavior that changes user-visible state

Prefer one use case per button or action. If multiple buttons are variants of the same workflow, each button must still be explicitly named in that shared use-case document.

Code that implements a use case should reference the document from KDoc with `@see docs/usecases/...` or a short `Use cases: UC-...` line where that is clearer.

Every use-case document must include a `## Tool Calls` section before `## Code Entry Points`.

If the workflow has model-callable behavior, list the canonical tool IDs and relevant actions there, for example `workspace:file` with action `search`. If the workflow is UI-only or has no direct model tool call, write `- None.` explicitly.

The build packages `docs/usecases/*.md` into runtime resources under `usecases/`. Agents can inspect those packaged documents through the `usecases` tool with `list`, `show`, and `search` actions, so user questions about Visual Agent functionality can be answered from the maintained product use cases instead of stale prompt text.
