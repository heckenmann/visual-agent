# UC-0000094: Use a Codex CLI Subscription Provider

## Goal

Allow a desktop user to use an existing ChatGPT/Codex subscription in Visual Agent through the official, user-local Codex CLI. Visual Agent must make this dependency explicit, detect an existing installation, accept a manually selected executable, offer an explicit user-approved installation when the CLI is missing, guide the user through the official Codex login, and route every Codex model request through `codex app-server`.

This use case replaces the rejected direct consumer HTTP/SSE adapter. Visual Agent must not reproduce undocumented ChatGPT OAuth, account, token, header, model-catalog, or Responses transport behavior.

## Status

Planned. The current experimental direct Codex Responses implementation is not the target architecture and must be removed when this plan is implemented.

## Primary Actor

Desktop user with a ChatGPT plan that includes Codex access.

## Stakeholders

- The user, who expects subscription access without administrator privileges or manual token handling.
- Visual Agent, which requires a provider-neutral Spring AI chat and streaming contract.
- Codex CLI, which owns authentication, subscription entitlement, model discovery, upstream transport, and its credential cache.
- System administrators, who require explicit installation consent, bounded filesystem access, and clear process behavior.

## Preconditions

- The operating system can run a supported Codex CLI build.
- The user can reach the official Codex installer and ChatGPT login endpoints when installation or authentication is needed.
- Visual Agent can start a child process with the current user's permissions.
- A writable user home directory is available for the standard `~/.local/bin` and `~/.codex` locations.
- No administrator rights are assumed or requested.

## Product Boundary

The provider must be named and displayed as **Codex CLI**. Settings, errors, activity indicators, diagnostics, and provider summaries must make it clear that Visual Agent starts and communicates with the locally installed Codex CLI.

The provider is not:

- the existing OpenAI-compatible Platform API-key provider;
- a direct ChatGPT consumer HTTP client;
- an embedded copy of Codex;
- a hidden system-wide installation;
- a fallback billing route.

All Codex authentication and upstream model traffic remain inside the official CLI. Visual Agent never reads `~/.codex/auth.json`, browser cookies, access tokens, refresh tokens, account identifiers, or authorization headers.

## Configuration Contract

A Codex CLI provider profile stores:

- its generated Visual Agent provider ID;
- the display name;
- adapter type `CODEX_CLI`;
- an optional absolute CLI executable path;
- enabled/disabled state;
- discovered model metadata and the selected default model;
- non-secret provider options such as reasoning effort, sandbox policy, and startup timeout.

It must not store an API key or an upstream Codex URL. A blank executable path means automatic discovery. An explicit path always takes precedence and remains stable until the user changes it.

## CLI Discovery

When a Codex CLI profile is opened, selected, tested, or used, Visual Agent resolves the executable in this order:

1. The explicit absolute executable path in the provider profile.
2. A `codex` executable found on the inherited `PATH`.
3. The official user-local default: `~/.local/bin/codex` on macOS/Linux.
4. Documented platform-specific user-local candidates supported by the official installer.

Visual Agent must not recursively scan the disk. Every candidate must be normalized and validated as a regular executable file. Validation runs `<candidate> --version` with a bounded timeout and accepts only a successful process with a recognizable Codex CLI version response.

Discovery results are classified as:

- `NOT_INSTALLED`;
- `INVALID_EXPLICIT_PATH`;
- `INSTALLED_NOT_AUTHENTICATED`;
- `READY`;
- `UPDATE_RECOMMENDED` when a future minimum version is not met;
- `UNSUPPORTED_PLATFORM`;
- `CHECK_FAILED` with a sanitized reason.

Changing the manual path triggers validation immediately. An invalid explicit path must not silently fall back to a different installation; the UI must let the user clear it to return to automatic discovery.

The provider editor displays the normalized installed version from `<candidate> --version` and the latest official package version through `npm view @openai/codex version --json`, with `yarn info @openai/codex version --json` as a fallback. Both commands are read-only registry queries with bounded execution and output. The lookup reports the latest version as unavailable when neither package manager can complete the query. It classifies a valid comparison as **Up to date** or **Update available**. Version discovery never starts an installation or update automatically.

## Installation Flow

1. Visual Agent shows that Codex CLI is missing and explains that the official CLI is required for this provider.
2. The user can choose **Install Codex CLI**, **Choose existing executable**, or **Cancel**.
3. Installation starts only after explicit confirmation showing:
   - publisher: OpenAI;
   - official download host;
   - target executable path;
   - Codex data directory;
   - that a child installer process will run with user permissions.
4. A Spring `WebClient`-backed installer service downloads the current official platform installer to a private temporary file. It must reject non-HTTPS URLs, unexpected final hosts, oversized responses, and HTTP errors.
5. Visual Agent executes the downloaded installer as an argument array, never as a concatenated shell command. It sets the user-local installation target explicitly and never invokes `sudo`, elevation, or a system package manager.
6. macOS/Linux defaults:
   - executable target: `~/.local/bin/codex`;
   - Codex home: `~/.codex`.
7. Windows uses the official PowerShell installer in the current user's context and its documented user-local destination.
8. Installer stdout/stderr is bounded, sanitized, and presented as progress or a concise error. Raw environment variables are never shown.
9. The temporary installer is deleted on success, failure, cancellation, and application shutdown.
10. Visual Agent validates the installed executable with `codex --version` and saves the resulting path only after successful validation.
11. If automated installation cannot start, the UI offers **Open installation instructions** and **Choose executable**. It must never report installation success without validating the binary.

Installation and update are separate, user-visible actions. Visual Agent must not install or update Codex automatically during startup or while sending a chat request.

## Authentication Flow

1. Visual Agent checks authentication with `codex login status`.
2. Exit code `0` means credentials are present; non-zero means authentication is required or the check failed.
3. The user selects **Sign in with ChatGPT**.
4. Visual Agent starts `codex login`, which owns the official browser OAuth flow and localhost callback.
5. For environments where the normal callback cannot work, the UI can offer **Sign in with device code**, which starts `codex login --device-auth` and renders the official URL and one-time code from sanitized process output.
6. Login has an explicit cancel action, a bounded overall timeout, and clear browser/device-code progress.
7. After the login process exits, Visual Agent reruns `codex login status` and refreshes `model/list`. Process exit alone is not considered proof of authentication.
8. **Sign out** starts `codex logout` only after confirmation and then refreshes provider state.

Visual Agent must not parse or copy the CLI credential cache. Authentication remains valid across Visual Agent restarts because it is managed by Codex CLI.

## Library Evaluation: CoKit

The required library review identified [CoKit](https://github.com/vupoint/cokit), a Kotlin Multiplatform app-server client. Its published Maven Central artifacts are `io.github.vupoint.cokit:cokit-client` and `io.github.vupoint.cokit:cokit-transport-stdio`, version `0.0.2` at the time of this review.

CoKit is a strong candidate for the typed app-server protocol layer. Its documented scope includes JSON-RPC request correlation, the `initialize`/`initialized` handshake, typed `thread/start`, `turn/start`, `turn/interrupt`, `model/list`, typed notifications including agent-message deltas and turn completion, a JVM stdio JSONL transport, and deny-by-default handlers for approval-like server requests. It also provides fake transports and a guarded real-CLI integration test. These capabilities should replace a Visual Agent-owned JSONL codec, response router, request-ID map, and basic approval wire encoding.

CoKit does not satisfy the complete product scope on its own:

- It does not discover an executable, validate a manually selected path, install or update Codex CLI, or render setup/login/approval UI.
- It is not a Spring AI `ChatModel` or `StreamingChatModel`; Visual Agent still needs the Spring adapter and the existing `LLMProvider` router.
- Its released stdio transport launches `ProcessBuilder(command)`, inherits the current environment, and only adds environment entries. It cannot remove inherited `OPENAI_API_KEY` and `OPENAI_CODEX_API_KEY`. Visual Agent must not use that process-launch constructor until CoKit supports an environment-replacement/sanitization policy or exposes a public transport constructor that can receive a process created by `CodexCliProcessFactory`.
- Its current public documentation describes the project as early development. The repository has no GitHub release objects, two published Maven versions (`0.0.1`, `0.0.2`), and a small maintenance footprint. Pin an exact version, use only Maven Central artifacts, and add an adapter compatibility suite against the supported Codex CLI version before adoption.
- CoKit's Maven Central POM declares Apache-2.0, but the GitHub repository did not expose a `LICENSE` file during this review. Confirm the license grant with the maintainer or legal review before adding the dependency.
- Dynamic tool compatibility and some protocol surfaces remain experimental/version-sensitive. Visual Agent must retain explicit capability negotiation and fail-closed behavior.

The klibs.io catalog was searched again for alternatives with the terms `codex`, `app-server`, `json-rpc`, `jsonrpc`, `stdio`, `openai`, and `chatgpt`. No second Kotlin library implements the Codex app-server protocol. The resulting alternatives are deliberately not substitutes:

- `agentclientprotocol/kotlin-sdk` implements ACP, not the Codex app-server protocol. The supported local Codex CLI command list has no `acp` command, while the documented provider route is `codex app-server --stdio`; adopting ACP would require a separate product and protocol decision.
- `modelcontextprotocol/kotlin-sdk`, `mcp4k`, and `codex mcp-server` implement MCP. MCP exposes tools/resources between clients and servers; it is not a chat-provider protocol for using the authenticated Codex CLI as an `LLMProvider`.
- `rpc-core`, `jsonrpc-kotlin-client`, `xqt-kotlinx-json-rpc`, `jsonrpc`, and `kmp.jsonrpc` implement generic JSON-RPC only. They provide neither Codex's typed protocol model nor an app-server session/client layer. Adopting one would require Visual Agent to implement all Codex requests, notifications, capability negotiation, approval handling, and tests itself.
- OpenAI API clients, including `openai-kotlin` and `openai-kmp`, target direct HTTP API endpoints. They do not use the user-local CLI or its authenticated subscription and are explicitly outside this issue's architecture.

Decision: evaluate CoKit first for the protocol/client layer through a dependency proof of concept. Do not depend on it for process lifecycle, environment sanitization, installation, login UX, provider UI, Spring AI adaptation, or product policy. Adopt it only after its released public API can be used with a safely sanitized child process and passes Visual Agent's protocol, cancellation, approval, and shutdown tests. Otherwise implement only the missing minimal app-server transport boundary locally.

## Spring Integration Architecture

The planned implementation uses constructor-injected Spring beans:

- `CodexCliLocator`: resolves and validates explicit, `PATH`, and user-local executable candidates.
- `CodexCliInstaller`: downloads the official installer with Spring `WebClient`, runs it with user permissions, and validates the result.
- `CodexCliAccountService`: runs login status, browser login, device-code login, and logout operations.
- `CodexCliProcessFactory`: creates sanitized, bounded Codex child processes and is the only component allowed to start them.
- `CodexAppServerConnection`: owns one initialized JSONL-over-stdio app-server connection and request correlation.
- `CodexAppServerManager`: lazily starts, monitors, restarts, and shuts down connections by resolved executable/configuration.
- `CodexCliChatModel`: implements Spring AI `ChatModel` and `StreamingChatModel`, mapping Spring AI prompts, responses, metadata, tools, and cancellation to the app-server protocol.
- `CodexCliProvider`: implements the existing Visual Agent `LLMProvider` boundary by delegating chat and streaming to `CodexCliChatModel`, matching the structure of other Spring AI-backed providers.

The adapter is selected through `ConfiguredLLMProvider` only for `CODEX_CLI`. It must never fall back to Ollama or the OpenAI-compatible provider after a CLI, authentication, model, quota, or protocol failure.

## Child Process Contract

Every Codex process starts with:

- the resolved executable as the first argument;
- arguments passed as an immutable list, without command-string interpolation;
- a deliberate working directory;
- inherited user environment except variables that could silently change authentication or billing;
- `OPENAI_API_KEY` and `OPENAI_CODEX_API_KEY` removed;
- stdout and stderr consumed concurrently to prevent deadlocks;
- bounded line length, output size, startup timeout, idle timeout, and operation timeout;
- process-tree termination on cancellation, timeout, application shutdown, or unrecoverable protocol failure.

No command line may contain credentials. stderr is diagnostic only and must never be interpreted as JSON-RPC. Diagnostics are sanitized before they reach logs or the UI.

## App-Server Transport Contract

Visual Agent starts:

```text
codex app-server --stdio
```

The official transport is bidirectional JSON-RPC 2.0-style JSONL over stdin/stdout, with the `jsonrpc` field omitted. Each stdout line is one complete JSON message. stdin writes are serialized through one writer lock.

### Initialization

Immediately after process start, Visual Agent sends exactly one `initialize` request with:

- a unique numeric request ID;
- `clientInfo.name = "visual_agent"`;
- `clientInfo.title = "Visual Agent"`;
- the Visual Agent application version;
- only explicitly required capabilities.

After a successful response, Visual Agent sends the `initialized` notification. No other method may be sent first. Visual Agent should stay on the stable API surface unless a separately tested feature, such as dynamic Spring AI tool bridging, requires explicit `experimentalApi` opt-in.

OpenAI's app-server documentation requests that enterprise integrations identify their client and contact OpenAI for inclusion in the known-client list. This product/legal follow-up remains part of release readiness.

### Request correlation

- Numeric IDs increase monotonically per connection.
- A concurrent map correlates response IDs to pending Reactor sinks/futures.
- Unknown response IDs, duplicate terminal responses, malformed lines, and server errors are protocol failures.
- Server notifications without IDs are dispatched by method and thread/turn identifiers.
- Server-initiated requests with IDs must receive exactly one response.

### Model discovery

Visual Agent sends:

```json
{"method":"model/list","id":N,"params":{"limit":100,"includeHidden":true}}
```

It follows `nextCursor` until exhausted, deduplicates by model ID, and stores every model returned for the active account and client context, including entries marked hidden by Codex. It stores display name, default status, supported reasoning efforts, input modalities, and personality support where the provider catalog can represent them. An empty authenticated result is an error and must not erase a previously valid catalog.

The model marked `isDefault` becomes the suggested default only when the user has not already selected a valid model.

### Conversation mapping

Each Visual Agent request uses an ephemeral Codex thread so Visual Agent remains the authoritative conversation store and Codex does not create an additional persistent transcript by default.

1. Send `thread/start` with the selected model, current workspace directory, service name, approval policy, and bounded sandbox configuration.
2. Map prior Visual Agent system/developer/user/assistant messages into explicit thread input/history items without rewriting Markdown.
3. Send the current user input with `turn/start`.
4. Preserve message order and reject unsupported rich content rather than serializing arbitrary objects.
5. Include the selected model and supported reasoning effort. Unsupported sampling options are ignored only when documented; otherwise the UI explains that Codex CLI does not support them.

If the installed app-server version does not support ephemeral threads, Visual Agent must either use a fresh thread and archive/delete it after completion or fail with a compatibility error. It must not silently accumulate hidden persistent threads.

### Streaming

After `turn/start`, `CodexCliChatModel.stream()` converts ordered app-server notifications into a Reactor `Flux<ChatResponse>`:

- `turn/started`: record the active turn ID;
- `item/agentMessage/delta`: emit assistant text delta;
- reasoning summary events: map to provider metadata, not user-facing answer text unless the UI requests it;
- `item/completed`: retain authoritative final item state;
- `model/rerouted`: update response metadata and show the effective model;
- `turn/completed` with `completed`: complete the Flux exactly once;
- `turn/completed` with `interrupted`: terminate as cancellation;
- `turn/completed` with `failed`: emit a sanitized provider error.

The non-streaming `call()` path consumes the same event sequence, assembles assistant text in order, and returns only after successful `turn/completed`. It must not invoke a separate upstream API.

### Spring AI tool bridge

Visual Agent tool execution remains in the host application, while every model decision remains inside Codex CLI.

- Spring AI `ToolCallback` definitions are mapped to app-server dynamic tool declarations only when the installed protocol version supports them.
- Visual Agent explicitly opts into the required experimental app-server capability; this compatibility boundary is tested and shown in diagnostics.
- `item/tool/call` server requests are correlated to the active turn and dispatched through the existing Spring `ToolCallback`/`ToolRegistry` execution policy.
- Tool results are returned as structured content through the matching app-server response ID.
- Unknown tools, invalid arguments, duplicate requests, timeout, cancellation, or tool exceptions return a sanitized tool failure, never a fabricated success.
- When dynamic tools are unsupported, provider activation must clearly report reduced capability or fail according to the selected compatibility policy; it must not silently expose incorrect tool definitions.

### Built-in Codex approvals

Command execution, file changes, permissions, user input, MCP elicitation, and other server-initiated approval requests are never auto-approved.

- Requests are routed to a modal that displays the operation, scope, working directory, network destination, and available decisions.
- The user can approve once, approve for the supported scope, decline, or cancel when the protocol offers those choices.
- Closing the dialog, timing out, cancelling the turn, or losing the app-server connection resolves the request as declined/cancelled.
- Visual Agent responds to the exact server request ID once and waits for `serverRequest/resolved`/item completion.
- Default sandbox access is the minimum needed for the selected Visual Agent workflow. Full access and bypass modes are never selected implicitly.

## Cancellation

Cancelling a Visual Agent request sends:

```json
{"method":"turn/interrupt","id":N,"params":{"threadId":"...","turnId":"..."}}
```

Visual Agent then waits for `turn/completed` with `status: "interrupted"` for a short bounded grace period. If the server does not acknowledge, it terminates the app-server process and fails every in-flight request on that connection as cancelled/connection-lost. No late delta may reach the UI.

A cancelled request is never retried automatically because replay could duplicate model usage, tool calls, file changes, or commands.

## Connection Lifecycle

- App-server startup is lazy and occurs only after an explicit setup action or first provider operation.
- One managed connection may serve concurrent requests if the protocol version supports it; request and notification routing must remain isolated by IDs, thread IDs, and turn IDs.
- A crashed or exited process fails all in-flight requests with one stable connection-lost category.
- The manager may restart for a later user action, but never replay the failed turn automatically.
- Changing the executable path or Codex home drains and replaces the old connection.
- Application shutdown first interrupts active turns, closes stdin, waits briefly, then terminates the process tree.
- Health checks use initialization, account/login status where available, and `model/list`; process existence alone is not readiness.

## Error Handling

The provider maps failures into stable user-facing categories:

- CLI missing: offer installation or path selection.
- Invalid manual path: preserve the value, explain validation failure, and offer clear/reset.
- Permission denied or non-executable file: explain required file permissions.
- Unsupported CLI version: offer update and show installed/minimum versions.
- Installer download failure: preserve existing installations and offer retry/manual instructions.
- Installer failure/cancellation: show bounded sanitized output and do not save an unvalidated path.
- Login required/expired: offer sign-in; do not fall back to API-key billing.
- Browser callback failure: offer device-code login.
- Device code expired/denied: return to signed-out state without retrying automatically.
- App-server startup timeout or early exit: show CLI path/version and sanitized stderr category.
- Handshake mismatch or unsupported method: identify protocol incompatibility and recommend CLI update.
- Malformed/oversized JSONL: terminate the connection and fail closed.
- Unknown server request: respond with a protocol error/decline and fail the affected turn safely.
- Model unavailable/rerouted: refresh the model catalog and show the effective result without silent provider fallback.
- Subscription entitlement/quota/rate limit: preserve the Codex provider selection and show the CLI-supplied sanitized category.
- Tool or approval timeout: return a declined/failed result and keep request correlation intact.
- Child process crash: fail every associated operation; restart only on a later explicit operation.
- User cancellation: interrupt the turn and never replay it.

Errors and logs must not contain credentials, raw environment values, full auth output, account identifiers, or unbounded prompts/responses.

## User Experience

The Codex CLI setup card displays one state at a time:

- **Codex CLI not installed** — Install / Choose executable.
- **Codex CLI path invalid** — Choose another / Clear custom path.
- **Codex CLI installed, sign-in required** — Sign in with ChatGPT / Device code.
- **Signing in** — browser/device instructions and Cancel.
- **Ready** — validated path, CLI version, authenticated status, Refresh models, Sign out.
- **Update recommended** — Update / Continue when compatible.
- **Error** — concise explanation, Retry, Diagnostics, and the relevant recovery action.

The UI must clearly label installation and app-server activity as Codex CLI activity. It must not mention direct Responses transport. Provider selection does not silently install, sign in, or start a billable model turn. Model refresh may start app-server but does not send a model prompt.

## Main Flow

1. The user adds or selects **Codex CLI** in provider settings.
2. Visual Agent automatically discovers and validates an existing CLI, or the user chooses its executable.
3. If missing, the user explicitly starts the official user-local installation.
4. Visual Agent validates the installed version.
5. If signed out, the user starts the official browser or device-code login.
6. Visual Agent verifies login status without reading credentials.
7. Visual Agent starts and initializes `codex app-server --stdio`.
8. `model/list` populates the provider model catalog.
9. The user selects a model and optional supported reasoning effort.
10. Chat and streaming requests are mapped through the Spring AI `CodexCliChatModel` to app-server threads and turns.
11. Streaming deltas, tools, approvals, completion, errors, and cancellation are mapped back into existing Visual Agent contracts.
12. The app-server process is shut down safely when no longer needed or when Visual Agent exits.

## Result

The user can use their Codex subscription in Visual Agent through the official Codex CLI without administrator rights or manual credential handling. The dependency, executable path, version, login state, installation actions, and process activity remain visible and controllable. No model request bypasses Codex CLI.

## Tool Calls

- Visual Agent tools are exposed through the planned Spring AI/app-server dynamic tool bridge when protocol compatibility is confirmed.
- Built-in Codex command, file, permission, and MCP actions remain governed by app-server approval requests and Visual Agent confirmation dialogs.

## Code Entry Points

Implemented:

- `de.heckenmann.visualagent.agent.LLMProvider`
- `de.heckenmann.visualagent.agent.ConfiguredLLMProvider`
- `de.heckenmann.visualagent.agent.provider.ProviderAdapter.CODEX_CLI`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`
- `de.heckenmann.visualagent.agent.codex.CodexCliLocator`
- `de.heckenmann.visualagent.agent.codex.CodexCliAccountService`
- `de.heckenmann.visualagent.agent.codex.CodexCliProcessFactory`
- `de.heckenmann.visualagent.agent.codex.CodexCliCoKitTransport`
- `de.heckenmann.visualagent.agent.codex.CodexAppServerConnectionFactory`
- `de.heckenmann.visualagent.agent.codex.CoKitCodexAppServerChatBridge`
- `de.heckenmann.visualagent.agent.codex.CodexCliChatModel`
- `de.heckenmann.visualagent.agent.codex.CodexCliProvider`
- `de.heckenmann.visualagent.agent.codex.CodexModelCatalogInitializer`
- `de.heckenmann.visualagent.ui.compose.ComposeProviderProfileEditor`
- `de.heckenmann.visualagent.ui.compose.ComposeSettingsPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeSettingsProviderSection`

Planned follow-ups:

- `de.heckenmann.visualagent.agent.codex.CodexCliInstaller`
- Dynamic Visual Agent tool bridging and app-server approval dialogs.

The existing experimental `CodexResponsesClient`, `CodexResponsesTransport`, `CodexResponsesModels`, and environment-backed `CodexSessionResolver` are removed as part of implementation.

## External Protocol References

- [Codex app-server](https://developers.openai.com/codex/app-server)
- [Codex CLI](https://developers.openai.com/codex/cli)
- [Codex CLI reference](https://developers.openai.com/codex/cli/reference)
- [Codex authentication](https://developers.openai.com/codex/auth)
- [Codex IDE extension](https://developers.openai.com/codex/ide)
- [Spring AI Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
- [Spring WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

## Acceptance Criteria

- The provider and every related UI surface are named **Codex CLI** and clearly disclose the local CLI dependency.
- Existing CLI installations are detected by explicit path, `PATH`, and documented user-local defaults.
- Users can choose and validate a custom executable; invalid custom paths never silently fall back.
- Missing CLI installations can be started from Visual Agent after explicit informed consent, without administrator rights.
- Installation uses the official HTTPS source, user-local targets, bounded output, validation, cleanup, and no privilege escalation.
- Browser and device-code login use official CLI commands; Visual Agent never reads or stores Codex credentials.
- `codex login status` and `model/list` determine readiness; process existence alone does not.
- All model discovery, chat, streaming, tools, approvals, and cancellation use `codex app-server`; no direct consumer HTTP/SSE path remains.
- The provider is implemented as constructor-injected Spring beans with a Spring AI `ChatModel`/`StreamingChatModel` adapter.
- JSONL request IDs, server requests, notifications, thread IDs, turn IDs, timeouts, malformed input, and process crashes are handled deterministically.
- `OPENAI_API_KEY` and `OPENAI_CODEX_API_KEY` are removed from every Codex child-process environment.
- Cancellation sends `turn/interrupt`, waits for terminal interruption, then terminates the process if necessary; cancelled turns are never replayed.
- Built-in Codex actions are never auto-approved, and unresolved prompts default to decline/cancel.
- Dynamic Visual Agent tools are bridged through Spring AI/app-server only with explicit compatibility handling.
- Empty or failed model refreshes do not erase a previously valid catalog.
- No failure silently falls back to another provider or billing path.
- Tests cover discovery precedence, path validation, installation confirmation/results, login states, environment sanitization, process lifecycle, handshake, pagination, streaming order, tool requests, approvals, interruption, protocol errors, timeout, crash, restart, and shutdown.
- A real manual smoke test verifies existing-install detection, fresh user-local installation, browser login, device-code fallback, model discovery, one streamed turn, cancellation, sign-out, and application shutdown before release.
