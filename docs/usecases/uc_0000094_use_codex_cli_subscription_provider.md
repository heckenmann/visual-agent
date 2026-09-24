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
3. The adapter negotiates `initialize`, starts an ephemeral thread, injects completed conversation turns as role-preserving Responses API messages, and starts the current turn with enabled dynamic tools.
4. Native `item/agentMessage/delta` notifications are mapped to incremental Spring AI responses while the turn is running.
5. `item/tool/call` requests are validated against the request-scoped tool allowlist and delegated to the existing `ToolRegistry` callback.
6. The tool result is returned to the same Codex turn through `DynamicToolCallResponse`; the trusted workspace image action becomes an `inputImage` content item. Audio content remains textual until the negotiated app-server schema and selected model support it.
7. Assistant deltas retain their Codex `itemId` in Spring AI response metadata so separate assistant items remain distinguishable. When two different items contain visible assistant text, the stream adds a Markdown blank-line boundary unless existing whitespace already separates them.
8. `turn/completed` emits the terminal Spring AI response and the process is closed.

## Prompt and input mapping

System messages are sent as Codex `baseInstructions`. Completed user and assistant messages are converted to native Responses API message items and appended to the ephemeral thread with `thread/inject_items`, preserving their roles and order. The latest user turn is sent as a native user text input through `turn/start`, whose input contract accepts user input items rather than arbitrary Spring message roles; any additional assistant/context messages in that active turn retain an explicit role marker. No API key or provider credential is inserted into the prompt. An injection failure fails the provider request instead of silently degrading to flattened history.

### Context transfer research

- Spring AI models a `Prompt` as an ordered collection of role-bearing messages. A previous assistant response is a distinct assistant-role message, not user text with an `[assistant]` prefix.
- Spring AI's `MessageChatMemoryAdvisor` adds retrieved conversation history to a prompt as a collection of messages. Its `VectorStoreChatMemoryAdvisor` instead appends retrieved memory to system text, which is not the appropriate representation for turn-by-turn dialogue.
- Visual Agent already persists the complete conversation in its database and applies context policies and token-window budgeting in `MainAgentContextAssembler` and `RequestContextBudgeter`. A second Spring `ChatMemory` store would duplicate ownership and bypass that request-specific selection, so the provider adapter preserves the prepared prompt instead.
- Codex app-server `turn/start` accepts `UserInput` items, not arbitrary Spring message roles. Its `thread/inject_items` RPC accepts raw Responses API items and appends them to model-visible thread history without starting a user turn. The adapter uses that operation for completed turns and sends only the current user turn through `turn/start`.
- The protocol was smoke-tested against the installed Codex CLI 0.150.1 using an ephemeral thread; injection succeeded without making a model-generation request.

## Configuration

The provider profile supports:

- `codex.executable.path`: optional absolute path to the Codex executable.
- `defaultModel`: optional model identifier. When blank, the Codex CLI chooses its configured default; no synthetic model is added to the selector.

The provider model catalog continues to use the official `codex debug models` command. Only model identifiers and display names are retained; raw catalog output is never logged or persisted.

Each request receives the server-managed workspace directory (or its request-scoped `workingDirectory` override), an ephemeral Codex thread, read-only sandboxing, and the `never` approval policy. The app-server process is closed after success, failure, cancellation, timeout, or application shutdown.

## Library and protocol decision

The former `org.springaicommunity.agents:agent-codex` dependency was removed. Its public API exposed synchronous `codex exec` execution but not the app-server protocol, native response deltas, or request-scoped dynamic tool callbacks.

Visual Agent now contains a clean-room adapter implemented only against public Spring AI APIs and the public Codex app-server schema. It does not copy, translate, or derive code from the removed connector. The adapter implements the required Spring AI chat and streaming model contracts and owns a minimal JSON-RPC process transport.

The app-server protocol supports request-scoped `dynamicTools`, `thread/inject_items` for adding raw Responses API history items without starting a user turn, server-initiated `item/tool/call` requests, textual and inline image tool results, and native `item/agentMessage/delta` streaming. Audio content-item mapping is retained as a disabled forward-compatibility path because current Codex schemas and models do not support audio. Unknown notifications are ignored for forward compatibility; unsupported server requests receive a protocol error.

## Tool Calls

- `item/tool/call` is handled by the server-owned Spring AI callback bridge.
- Only tools in `ChatRequestContext.enabledTools` are advertised and executable.
- Tool lifecycle events continue to use the existing `ToolRegistry` and `ToolEventBus`.
- The trusted `workspace:file` `imageBytes` action is converted to a data URL and sent as a protocol-native `inputImage` item.
- Audio tool results remain text until Codex negotiates an audio-capable protocol and model; no audio tool is advertised currently.

## Code Entry Points

- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliLocator.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliProcessFactory.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexAppServerProtocol.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexAppServerTransport.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexAppServerChatModel.kt`
- `modules/provider-openai-codex/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexDynamicToolResultMapper.kt`
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

- Protocol tests use a controlled fake app-server process and cover initialization, native delta streaming, assistant item boundaries, textual and inline image tool callbacks, structured tool failures, audio fallback, terminal completion, and cleanup.
- Streaming tests verify separate Codex items receive a Markdown boundary, same-item token chunks remain unchanged, and existing leading whitespace or blank lines are preserved without duplication.
- Request-boundary tests verify that every thread is ephemeral, read-only, uses the `never` approval policy, that completed assistant/user messages are injected with native roles before the current user turn, and that a server tool request outside the request-scoped allowlist is rejected. The CLI process-factory test verifies API-key removal with controlled sentinel values.
- Lifecycle tests verify successful completion process termination and failed-turn/rejected-tool error propagation. The transport cleanup path also waits for normal or forced child termination, and cancellation is wired to that same close path.
- Provider wiring tests cover the new dependency-free adapter.
- The optional real-CLI smoke test remains outside the default suite because it requires a locally authenticated Codex account; enable it with `-Dvisualagent.codex.smoke=true` and `-Dvisualagent.codex.smoke.model=...`. An executable path can be supplied with `-Dvisualagent.codex.smoke.executable=...`. The authenticated smoke test was run successfully against the locally installed CLI.

## References

- [Spring AI Prompt and message roles](https://docs.spring.io/spring-ai/reference/api/prompt.html)
- [Spring AI chat memory and message-history guidance](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Codex app-server thread protocol](https://github.com/openai/codex/blob/main/codex-rs/app-server-protocol/src/protocol/v2/thread.rs)
- [Codex app-server RPC definitions](https://github.com/openai/codex/blob/main/codex-rs/app-server-protocol/src/protocol/common.rs)
- [OpenAI tool and streaming reference](https://developers.openai.com/api/reference/cli/resources/responses/methods/create)
