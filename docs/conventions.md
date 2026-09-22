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

## Asynchronous Code

Server-side asynchronous service and event contracts use Reactor `Mono` and `Flux` when an operation is naturally asynchronous. Preserve native Reactor streams from Spring AI, WebClient, and R2DBC instead of converting them to coroutines only to convert them back at another server boundary.

Provider discovery, connectivity checks, model details, embeddings, vision, chat, and
streaming use Reactor contracts as well. Blocking provider SDK calls are wrapped once
inside the provider adapter and scheduled on the shared bounded-elastic scheduler.

Pure local transformations remain ordinary Kotlin. Unavoidable blocking server integrations must be isolated in explicit adapters scheduled through Reactor's standard `Schedulers.boundedElastic()` path. The server enables Reactor's Java 21+ virtual-thread bounded-elastic implementation through `reactor.schedulers.defaultBoundedElasticOnVirtualThreads=true` before the Spring context creates reactive services; an explicit JVM property takes precedence. Do not scatter `subscribeOn`, create custom virtual-thread executors, or block a reactive pipeline without a documented boundary reason.

Use `ToolRegistry.executeReactive` for server-side tool execution and `ToolEventBus.events` for tool lifecycle subscriptions. `executeBlocking` is reserved for synchronous host callback APIs such as Spring AI and must not become a general server execution path.

Reactor terminates at the server transport boundary. Generated Java gRPC services still
use `StreamObserver`; Spring gRPC registers `BindableService` implementations but does
not automatically adapt `Mono` or `Flux` method returns. The server transport adapter
must therefore translate Reactor signals explicitly and tie subscription cancellation
to the gRPC session. `:protocol`, `:desktop`, and `:ui` remain Reactor-free and use
protocol callbacks, Kotlin `Flow`, `StateFlow`, `SharedFlow`, and Compose state as
appropriate. A `Flux` to `Flow` bridge is valid only in a desktop/client adapter or
another genuine external boundary.

Every server event stream must document whether events are mandatory, replayed, coalesced, buffered, or dropped for slow consumers. State queries must remain separate from transient event streams.

## Provider Neutrality

Do not add production branches for individual model names, model families, or observed output
quirks. Provider behavior must be selected through the provider adapter, declared capabilities,
structured response metadata, or documented protocol differences. Normalize protocol-level framing
artifacts once at the shared provider-neutral boundary before rendering, persistence, or history
reuse. If structured information is unavailable, return a clear failure or improve the common
contract; do not introduce a model-specific workaround.
