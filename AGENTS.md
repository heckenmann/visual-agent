# AGENTS.md

## Build & Run

- Build: `./gradlew build` (or `gradle build` after regenerating the wrapper).
- Run desktop app: `./gradlew :desktop:run` (Compose desktop; starts one embedded non-web Spring context with ANSI-colored Spring logs).
- Run standalone server: `./gradlew :application:runServer` (Spring Boot without Compose).
- Run all tests: `./gradlew test`.
- Run one test class/method: `./gradlew test --tests "de.heckenmann.visualagent.<path>.<TestClass>.<method>"`.
- Run smoke tests against real Ollama: `./gradlew test -Dvisualagent.ollama.smoke=true` (default `false`).
- Copy dependencies to `./lib/`: `./gradlew :application:copyAllDependencies`.
- Java toolchain is **JDK 24** (`jvmToolchain(24)`); CI runs on **JDK 21** to let the toolchain resolver fetch 24.
- The Gradle wrapper version is defined centrally in `gradle/wrapper/gradle-wrapper.properties`; CI reads that version when bootstrapping the wrapper. You only need a local Gradle installation to regenerate it.
- `gradle.properties` sets `org.gradle.daemon=false` (matches CI).

## Pre-Commit Quality Gates

Always run, in this order:
```bash
./gradlew ktlintCheck check test
```
- `ktlintCheck` depends on `ktlintJavadocCheck` and `unusedCodeCheck`.
- `check` depends on `locAndPackageSizeCheck` (blocking, no exceptions), `unusedCodeCheck` (blocking), `desktopApiUsageCheck` (blocking), `useCaseDocumentationCheck` (blocking), `jacocoTestCoverageVerification` (≥ 0.80 LINE; UI compose classes are included in the coverage calculation).
- `tasks.test` is finalized by `jacocoTestReport` and uses JUnit Platform.
- `generateUseCaseResources` runs as part of `processResources`; use cases are packaged to `build/generated/usecase-resources/usecases/`.
- CI runs the desktop Compose test path under `xvfb-run -a` because Compose needs an X server; locally on macOS / Windows / a Linux desktop this is unnecessary.

> **Agent efficiency note:** Full `./gradlew ktlintCheck check test` is slow and produces a lot of output. During iterative development, prefer running the relevant test class/method in isolation, e.g. `./gradlew test --tests "de.heckenmann.visualagent.<path>.<TestClass>.<method>"`. Run the complete gate only when the change is ready for commit or when CI-like verification is needed. The full gate must still pass before any commit.

## Commit Conventions

- Conventional Commits: `type(scope): short imperative summary`. Recurring scopes: `ui`, `agent`, `todo`, `knowledge`, `workspace`, `canvas`, `orchestration`, `docs`, `build`, `ci`.
- One logical change per commit. Do not mix refactors, UI work, and docs unless tightly coupled.
- When moving files, use `git mv` so Git tracks the rename instead of treating it as a delete/add pair.
- Examples: `feat(ui): add canvas zoom controls`, `fix(todo): persist status updates`, `refactor(knowledge): move persistence to repository stores`, `docs: update agent UX docs`.

## Pull Requests

- Create one focused PR per logical branch/change; do not open mega-PRs.
- Every manually created PR must be configured to **delete the source branch on merge** (`delete_branch_on_merge: true`).
- Dependabot PRs that meet all of the following conditions are auto-merged via the `dependabot-automerge` job in `dependabot-automerge.yml`:
  - Author is `dependabot[bot]`.
  - CI (`test` job) is green.
  - The update is a patch or minor bump (not major).
  - The package ecosystem is Gradle (not GitHub Actions).
  - Only dependency files (`**/build.gradle*`, `**/gradle.properties`, `**/libs.versions.toml`, `.github/workflows/*.yml`) are touched.
  - The PR does not have the `dependabot: no-auto-merge` label.
  - Auto-merge uses squash-merge and deletes the source branch.
- Other automated PRs (e.g. release-please) are left to their own lifecycle.
- Before creating a PR, verify the branch is not already merged into `master` and that no duplicate open PR exists for it.

## Issue Conventions

- Every issue must be created with at least one label. Never create an unlabeled issue.
- Available labels (see `.github/` or `gh label list`): `bug`, `enhancement`, `documentation`, `ui`, `dependencies`, `github_actions`, `status: research`, `status: in progress`, `good first issue`, `help wanted`, `duplicate`, `invalid`, `wontfix`.
- **`ui`** must be added to every issue that involves Compose desktop UI changes (new panels, modified panels, layout changes, theme/styling, animations, indicators). This includes issues whose primary work is backend but have a UI Changes section.
- **`enhancement`** is the default label for new features and improvements (the `improvement` label does not exist; use `enhancement`).
- **`bug`** is for defects in existing behavior.
- **`dependencies`** is for dependency-version-bump issues and Dependabot-related CI.
- **`status: research`** is used when the issue is an audit or investigation before implementation.
- Do **not** use the `java` label — the project is Kotlin-only and every issue touches Kotlin code, so the label carries no information.
- Issue titles use Conventional Commits format: `type(scope): short imperative summary`.

## Development Workflow

- Prefer database-level guarantees for consistency, ordering, locking, and persistence races whenever the database can solve the problem. Do not add a UI or Spring-server workaround for a problem that is already solvable at the database layer.
- Before starting work on a problem, check whether a GitHub issue and a documented use case exist for it. Link both in the PR description and commit messages where applicable.
- Before implementing any new function, research [klibs.io](https://klibs.io) and GitHub to determine whether a maintained library already implements the required capability. Evaluate protocol coverage, license, release maturity, compatibility with this project, and security implications before deciding to add a dependency or write a custom implementation. Record the decision in the relevant issue or use case when it materially affects the design.
- All changes must be developed on a topic branch (`codex/issue-<number>-short-description` or similar) and merged into `master` through a pull request. Never commit directly to `master`.
- When moving files, use `git mv` so Git tracks the rename instead of treating it as a delete/add pair.
- Project language is **English**. All code comments, documentation, commit messages, PR descriptions, issue comments, and use-case files must be written in English.
- Before committing and pushing, perform a manual smoke test of the changes and get explicit user confirmation that they are ready.
- After creating a commit that addresses an issue, update the issue with a comment summarizing what was implemented and what remains open.
- Never revert changes whose origin you do not know, but that make sense and are not obviously broken. If you are unsure, ask the user before reverting.

### Security

Never commit API keys, tokens, passwords, private keys, or user PII. Provider API keys (`ollama.api.key`, `openai.api.key`) are intentionally stored plaintext in SQLite by product decision; never expose them to model context, tool output, logs, or `AppConfig.exportTo()`.

## Prerequisites

- Java 21+ (toolchain auto-resolves JDK 24).
- A local Gradle installation is only needed to regenerate `gradlew`; the wrapper version is defined in `gradle/wrapper/gradle-wrapper.properties`.
- Ollama running (`ollama serve`) when using local Ollama, or a reachable remote Ollama endpoint.
- Optional: Ollama API key (bearer) when the endpoint requires authentication; non-blank keys are sent as `Authorization: Bearer <key>` on every request and apply live without restart (Base URL changes still require restart).

## Project Layout (essentials)

```text
application/src/main/kotlin/de/heckenmann/visualagent/
├── VisualAgentApplication.kt        # @SpringBootApplication and standalone server entry point
├── agent/                           # AgentManager, sub-agents, tools, conversation
│   ├── context/                     # MainSystemPromptComposer
│   ├── conversation/                # AgentConversationHistoryOps
│   ├── text/                        # AgentResponseCoordinator, ResponseRepetitionGuard
│   └── tools/                       # VisualAgentTool, ToolRegistry, ToolEventBus, all tools
├── orchestration/                   # AutonomousCoordinator, AutonomousTaskPlanner, UxSeedTasks
├── canvas/                          # CanvasOperations + InMemoryCanvasService + PNG/document codec
├── config/                          # AppConfig singleton, properties mapping, theme stylesheet IDs
├── image/                           # In-house RgbaPngEncoder (no AWT)
├── knowledge/                       # Domain models, JPA entities, stores, repositories, converters
├── todo/                            # Todo/TodoPriority/TodoStatus + TodoManager + Spring wiring
├── workspace/                       # File service, image header reader, PDF page renderer
│   └── layout/                      # Toolkit-neutral panel layout service + persistence
└── server/                          # Protocol ports and gRPC server adapters

modules/ui/src/main/kotlin/de/heckenmann/visualagent/
├── AppIdentity.kt                   # desktop identity and icon constants
└── ui/                              # Compose presentation-only panels (no ViewModel layer)

modules/desktop/src/main/kotlin/de/heckenmann/visualagent/desktop/
├── DesktopMain.kt                   # Compose desktop entry point
└── ComposeStartupHost.kt            # splash, endpoint selection, and local server ownership

modules/providers/src/main/kotlin/de/heckenmann/visualagent/agent/
├── LLMProvider.kt                   # unified chat/stream/vision/embeddings/getModels/getModelDetails
├── ConfiguredLLMProvider.kt         # @Primary provider router
├── provider/                        # ProviderCatalogService + catalog models + error messages
├── openai/                          # OpenAI-compatible adapter
└── ollama/                          # Ollama API client + bearer-auth filter

modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/
└── codex/                           # OpenAI Codex CLI subscription provider

modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/
└── ui/application/VisualAgentComposeApplication.kt # protocol-only Compose shell
```

See `README.md` for the full tree and the feature status table.

## Architecture (essentials)

- **Entry points**: `de.heckenmann.visualagent.desktop.DesktopMain` launches the Compose desktop host from `:desktop`; `de.heckenmann.visualagent.VisualAgentApplicationKt` launches the standalone Spring server from `:application`. In desktop mode, only one non-web Spring context from `:application` is created in the same JVM; the standalone entry point is an alternative process, not a second desktop server. The desktop host renders the splash first and starts or connects to the server asynchronously.
- **Agent core**: `agent/AgentManager.kt` is the facade (conversation ops + lifecycle ops + autonomy ops). `modules/providers/.../agent/ConfiguredLLMProvider.kt` is the `@Primary` Spring `LLMProvider`; it routes via `modules/providers/.../agent/provider/ProviderCatalogService` (DB-backed preference `llm.provider.catalog.v1`) to Ollama, OpenAI-compatible, or injected profile-aware provider adapters. The OpenAI Codex CLI adapter lives in `:provider-openai-codex`. Provider adapters: `OLLAMA`, `OPENAI_COMPATIBLE`, `CODEX_CLI`.
- **Tooling**: every tool is a `@Component` implementing `agent/tools/VisualAgentTool`. `ToolRegistry` adapts them to Spring AI `ToolCallback`s with STARTED/FINISHED events on `ToolEventBus`. `VisualAgentTool.managesExecution = true` opts out of the generic async/timeout wrapper (used by sub-agent execution tools).
- **Tool inventory** (canonical IDs): `ui`, `history`, `todos`, `context`, `pwd`, `manual`, `usecases`, `file:read`, `file:list`, `file:glob`, `file:grep`, `file:write`, `file:edit`, `terminal`, `sleep`, `browser` (placeholder, returns "not configured"), `search` (placeholder, returns "not configured"), `workspace:layout`, `workspace:file`, `workspace:mime`, `workspace:download`, `canvas`, `agent:list`, `agent:show`, `agent:create`, `agent:update`, `agent:delete`, `agent:log`. The main agent gets `agent:*` definition tools, `todos`, and the server-owned workspace transfer tools; sub-agents get role-based sets from `AgentToolConfigService` (default: `researcher`, `coder`, `analyst`). `tools.disabled.global` (preference) is a newline-separated blocklist applied to all agents.
- **Orchestration**: `orchestration/AutonomousCoordinator.kt` (constructed by `AgentManager`, reachable only through `AgentManagerAutonomyOps`). It uses `AutonomousTaskPlanner` (todo expansion + worker selection) and `UxSeedTasks.all()` (default UX backlog). Per-job retry loop is bounded by `agent.config.maxRetries`; result review calls the main LLM and expects `APPROVED` / `RETRY`. Concurrency is gated by `SubAgentJobScheduler` keyed off `AppConfig.maxParallelSubAgents`.
- **Persistence**: `knowledge/PersistenceStores.kt` defines domain `data class`es + `*Store` interfaces. `knowledge/PersistenceEntities.kt` holds the `@Entity internal class`es. `knowledge/JpaPersistenceStores.kt` + `JpaWorkspaceFileStore.kt` adapt Spring Data repositories to the domain interfaces. `KnowledgePersistenceConfig` creates the SQLite `DataSource` (Hikari, maxPool=1, WAL, `busy_timeout=5000`). `application/src/main/resources/db/migration/` holds `V1__initial_knowledge_schema.sql` and `V2__workspace_files.sql`. `application/src/main/resources/config/app.properties` is bootstrap-only and should contain only `database.path`; runtime config is in SQLite `user_preferences`. Conversation search uses SQLite FTS5 with a `LIKE` fallback for invalid FTS input.
- **Stable data root**: Gradle desktop and standalone-server run tasks use the repository root as their working directory, so `./data/visual-agent.db` remains the single database across modules and refactors. Do not create module-local `application/data` or `modules/desktop/data` stores.
- **Workspace files**: `workspace/WorkspaceFileService.kt` imports/reads files in `./data/workspace/`. `workspace/ImageHeaderReader.kt` reads PNG/JPEG/GIF dimensions without AWT. `workspace/PdfPagePreviewRenderer.kt` renders PDF page text to PNG using a built-in 5×7 bitmap font + `image/RgbaPngEncoder.kt` (in-house RGBA encoder).
- **Workspace layout**: `workspace/layout/WorkspaceLayoutService.kt` + `WorkspaceLayoutPersistence.kt` keep the toolkit-neutral panel layout under preference key `ui.workspace.layout.v1` (JSON, versioned).
- **Canvas**: `canvas/CanvasOperations.kt` is the toolkit-neutral model-facing contract. `InMemoryCanvasService` is the current Compose-migration backend; `CanvasPngRenderer` rasterizes figures, `CanvasDocumentCodec` encodes editable `.canvas` JSON (versioned). The default document lives at `data/workspace/canvas/current.canvas` and is auto-saved on every mutation.
- **UI**: `modules/ui/.../ui/` contains the protocol-only Compose shell. There is no `ViewModel` layer; panels receive a `ComposePanelServices` bundle containing only protocol ports and presentation state. The desktop host resolves the server-side `ApplicationPort` and supplies that boundary to the UI. `ComposeSplitWorkspace` lays out panels deterministically (1 = full, 2–4 = stage + right inspector, 5+ = balanced columns, 16 dp gap). `Cmd/Ctrl+1..6` focuses panels, `Cmd/Ctrl+K` opens the command palette, `Esc` closes it.
- **Server proxy boundary**: every connection outside the presentation process (provider/network calls, persistence, workspace filesystem, tools, and remote services) is owned by `:application` and exposed through `:protocol` ports. `:ui` must never access those systems directly. `:desktop` may only select and connect to the configured local in-process or remote server endpoint; it must not duplicate server-side adapters or provider access.
- **In-flight indicator**: `InFlightStateHolder` is the UI-owned holder for "agent is waiting on something". Activity and agent lifecycle events arrive through `ActivityPort`; chat and todo progress use protocol callbacks. The header `InFlightIndicator` renders nothing when `totalActive == 0` and shows 1–3 pulsing dots whose period shortens with the number of in-flight activities.

## Patterns & Conventions

- **Threading**: network requests, database writes, file I/O, and any operation that may take longer than ~1ms must run on a background dispatcher (`Dispatchers.IO` or `Dispatchers.Default`). UI state updates (Compose `mutableStateOf` writes) must happen on `Dispatchers.Main`. Never block the main thread with a suspend call that waits on I/O — use `withContext(Dispatchers.IO/Default)` to shift the blocking work, then `withContext(Dispatchers.Main)` to publish results. The `onChunk` callback in streaming paths is called from a background dispatcher and must use `withContext(Dispatchers.Main)` for any Compose state writes.
- **Constructor DI**: required dependencies are direct `private val`/`private var` constructor properties; never reassign them in the class body.
- **Spring-managed beans**: every class that holds state or provides a service must be a Spring `@Component`, `@Service`, or `@Configuration` bean with constructor injection. No `object` singletons, no `lateinit var` for collaborators, no `AppConfig.instance` outside the bootstrap path. Exceptions: pure-Kotlin stateless utilities (`object` with only `const val` or pure functions), per-composition UI holders (`remember { }`), and data class factories.
- **DB-first reads**: history, todos, sub-agents, workspace files, preferences are loaded from DB on demand — no long-lived in-memory caches.
- **Tests**: only modify or delete existing tests when there is a clear reason (changed behavior, removed feature, or test is no longer correct). Every test deletion must be justified; do not drop tests simply because they fail after a change.
- **Markdown 1:1**: pass conversation message text straight to the `multiplatform-markdown-renderer` library (`com.mikepenz.markdown.m3.Markdown`); do not pre-normalize, rewrite, or heuristically transform before parsing. The library uses its own Markdown parser internally.
- **Icon-only actions**: Compose workspace action buttons use icons with descriptive tooltips and accessibility descriptions; do not reintroduce text-only action controls where an existing icon-only pattern applies.
- **No legacy desktop toolkit**: do not add `java.awt` / `javax.swing` / `javafx` / `pdfbox.rendering` / `apple.awt` imports. `desktopApiUsageCheck` will fail the build. The single `-Djava.awt.headless=false` JVM arg in `build.gradle.kts` is whitelisted.
- **No AWT-based image I/O**: use `image/RgbaPngEncoder.kt` and `workspace/ImageHeaderReader.kt` instead of `ImageIO` / `BufferedImage`. PDFBox is used only for text extraction; `pdfbox.rendering` is forbidden.
- **API-key handling**: never log, return, or include API keys in tool results or model context. `SettingsTool` reports only "configured / not configured". `AppConfig.exportTo()` strips API keys.
- **Provider key live updates**: non-blank `ollama.api.key` is sent as `Authorization: Bearer <key>` on every request; key changes apply without restart. Base URL changes still require restart.
- **Tool-name guard**: `OllamaPromptFactory` and `OpenAiPromptFactory` emit a system message listing the exact allowed function names for the request, so the model cannot invent variants.
- **File LOC policy**: 300 effective LOC per `.kt` file, 3000 per package. `locAndPackageSizeCheck` blocks on violations; no grandfathering or exceptions are allowed. Split files before they exceed the limit.
- **KDoc required**: every public declaration needs a `/** ... */` immediately above. `ktlintJavadocCheck` walks the file and reports missing KDoc.
- **No unused private declarations**: `unusedCodeCheck` flags them; suppress with `@Suppress("unused")` if intentionally retained.
- **kotlinx.serialization**: data classes passed through `Json.encodeToString` / `decodeFromString` must be annotated `@Serializable`. After `parseToJsonElement()`, navigate with `.jsonObject` / `.jsonArray` / `.jsonPrimitive` extensions.

## Model Context Payload

The model does not receive arbitrary global state. The main agent gets a request-scoped `ChatRequestContext` built in `AgentManager.buildMainRequest(...)` with:

- System prompt from `MainSystemPromptComposer` (resume hint, authoritative todo counters, current todo list, active provider/model, execution policy).
- Optional `userModelInstruction` system message from `AppConfig`.
- Recent DB-backed conversation history (max 20 messages; older rows are reachable via the `history` tool).
- Tool-name guard system message from the active provider's prompt factory.
- `enabledTools = agentToolConfigService.mainAgentTools()`.
- Metadata: `sessionId=main`, `agent=main`, optional `requestId`.

Sub-agents get their own `chatHistory + new turn`, `agentId/agentName/agentRole` metadata, and a role-based tool set.

## Use-Case Documentation

Every new user-visible function (toolbar button, panel button, menu action, command-palette action, tool call, autonomous workflow, persisted state change) must create or update a use-case under `docs/usecases/`. Filename pattern: `uc_\\d{7}_[a-z0-9_]+\\.md` (enforced by `useCaseDocumentationCheck`). Each file must include a `## Tool Calls` section before `## Code Entry Points`; write `- None.` when no tool-call path exists. The catalog is packaged into the build and exposed to enabled sub-agents through the `usecases` tool (`list`, `show`, `search`).
- Use `./scripts/next-use-case.sh <description>` to generate the next free filename. The script considers both existing files and deleted files in Git history and will never reuse a number.

## KDoc Example

Public APIs in this repo look like the real `LLMProvider` interface in `agent/LLMProvider.kt`:

```kotlin
/**
 * LLM provider interface for chat, streaming, vision, and embedding capabilities.
 *
 * @see OllamaClient for the local Ollama implementation
 * @see OpenAiClient for the OpenAI-compatible implementation
 */
interface LLMProvider {
    /**
     * Send a chat request with model, tool, and metadata context.
     *
     * @param request Complete request context for the provider
     * @return Complete chat response from the LLM
     * @throws Exception if the request fails or model is unavailable
     */
    suspend fun chat(request: ChatRequestContext): ChatResponse
}
```

## Status

The Compose migration is complete. The current desktop runtime is Compose Multiplatform and the desktop JavaFX/FXML/JHotDraw path has been removed. See `docs/compose-migration-audit.md` for the historical requirement audit and remaining follow-up work.

## Known Bugs

1. `browser` and `search` tools are placeholders that always return "not configured" until a real backend is wired (issues #16, #40).

## Gotchas

- Never use `sleep` in bash commands — use polling with `gh run view --json status` or similar non-blocking approaches instead.

- Stale WAL/SHM: if a crashed process leaves `data/visual-agent.db-wal` / `data/visual-agent.db-shm` and you see `SQLITE_BUSY`, delete those two files before restarting.
- GitHub CLI comment formatting: when posting issue/PR comments with `gh issue comment` / `gh pr comment`, use `--body-file path/to/file.md` (or pipe from stdin) instead of `--body "$(cat <<'EOF' ... EOF)"`. The latter double-escapes newlines and backticks, producing a single unformatted paragraph on GitHub.
- Tool error text comes from `agent/provider/ProviderErrorMessages.kt` (matches `429`/`403`/`401`/`timeout`/`connection refused`); do not embed raw SDK exception messages in tool results.
- `ProviderCatalogService` is the single source of truth for provider/model/variant at runtime; the legacy `AppConfig.ollama*` / `openai*` properties are still loaded but migrated into the catalog on first init.
- Spring AI's `Flux` is bridged to coroutines via `kotlinx-coroutines-reactor`; any new streaming path must preserve the existing `Flux → Flow` conversion.

## Documentation Language

All documentation and code comments are in **English**.

## Development Philosophy

Write software you would be happy to use yourself. Intuitive UI/UX, clear code, thoughtful error handling, performance that doesn't frustrate, and features that deliver real value.
