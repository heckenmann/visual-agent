# UC-0000094: Use the Codex CLI subscription provider

## Goal

Allow a desktop user to use an authenticated, user-local OpenAI Codex CLI installation as a Visual Agent provider. The provider uses a server-owned Spring AI adapter over the public Codex app-server protocol.

## Preconditions

- The user has installed and authenticated the official Codex CLI.
- The configured executable is an absolute executable path, or the CLI is discoverable on `PATH`/the user-local bin directory.
- The provider profile uses adapter `CODEX_CLI`.

## Main Flow

1. The provider locator validates the configured executable with `codex --version`.
2. The provider creates a short-lived app-server process with `codex app-server --listen stdio://`.
3. The adapter negotiates `initialize`, starts an ephemeral thread, and starts one turn with the request's messages and enabled dynamic tools.
4. Native `item/agentMessage/delta` notifications are mapped to incremental Spring AI responses while the turn is running.
5. `item/tool/call` requests are validated against the request-scoped tool allowlist and delegated to the existing `ToolRegistry` callback.
6. The tool result is returned to the same Codex turn through `DynamicToolCallResponse`.
7. `turn/completed` emits the terminal Spring AI response and the process is closed.

## Prompt and input mapping

System messages are sent as Codex `developerInstructions`. User and assistant messages are sent as turn text inputs; assistant history is explicitly marked with an `[assistant]` prefix because the app-server turn input contract accepts user input items rather than arbitrary Spring message roles. No API key or provider credential is inserted into the prompt.

## Configuration

The provider profile supports:

- `codex.executable.path`: optional absolute path to the Codex executable.
- `defaultModel`: optional model identifier. When blank, the Codex CLI chooses its configured default; no synthetic model is added to the selector.

The provider model catalog continues to use the official `codex debug models` command. Only model identifiers and display names are retained; raw catalog output is never logged or persisted.

Each request receives the server-managed workspace directory (or its request-scoped `workingDirectory` override), an ephemeral Codex thread, read-only sandboxing, and the `never` approval policy. The app-server process is closed after success, failure, cancellation, timeout, or application shutdown.

## Library and protocol decision

The former `org.springaicommunity.agents:agent-codex` dependency was removed. Its public API exposed synchronous `codex exec` execution but not the app-server protocol, native response deltas, or request-scoped dynamic tool callbacks.

Visual Agent now contains a clean-room adapter implemented only against public Spring AI APIs and the public Codex app-server schema. It does not copy, translate, or derive code from the removed connector. The adapter implements the required Spring AI chat and streaming model contracts and owns a minimal JSON-RPC process transport.

The app-server protocol supports request-scoped `dynamicTools`, server-initiated `item/tool/call` requests, textual tool results, and native `item/agentMessage/delta` streaming. Unknown notifications are ignored for forward compatibility; unsupported server requests receive a protocol error.

## Tool Calls

- `item/tool/call` is handled by the server-owned Spring AI callback bridge.
- Only tools in `ChatRequestContext.enabledTools` are advertised and executable.
- Tool lifecycle events continue to use the existing `ToolRegistry` and `ToolEventBus`.

## Code Entry Points

- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliLocator.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliProcessFactory.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexAppServerProtocol.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexAppServerTransport.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexAppServerChatModel.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliProvider.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliModelCatalog.kt`

## Error Handling

- Missing CLI: report that Codex is not installed.
- Invalid explicit path: report that the configured path is invalid and do not silently fall back to another executable.
- Non-zero process exit, protocol error, malformed JSON, unexpected EOF, timeout, and failed tool calls are converted into provider failures without exposing raw payloads in model context.
- Cancellation closes the active app-server process and causes the provider flow to terminate.
- Application shutdown closes active process transports and their reader coroutines.

## Security

- The executable path is validated before use.
- API-key environment variables are removed before starting the child process.
- Every request has a separate temporary working directory and ephemeral thread.
- Codex executes with read-only sandboxing and no automatic approvals.
- Tool names and arguments are checked against the request-scoped allowlist before execution.
- API keys, prompts, conversation history, and tool payloads are not logged.
- No repository-local third-party Codex source is compiled or executed.

## Verification

- Protocol tests use a controlled fake app-server process and cover initialization, native delta streaming, dynamic tool callbacks, terminal completion, and cleanup.
- Provider wiring tests cover the new dependency-free adapter.
- The optional real-CLI smoke test remains outside the default suite because it requires a locally authenticated Codex account; enable it with `-Dvisualagent.codex.smoke=true`, `-Dvisualagent.codex.smoke.executable=...`, and `-Dvisualagent.codex.smoke.model=...`.

## References

- [Codex app-server protocol schema](https://developers.openai.com/)
- [OpenAI tool and streaming reference](https://developers.openai.com/api/reference/cli/resources/responses/methods/create)
