# UC-0000094: Use the Codex CLI subscription provider

## Goal

Allow a desktop user to use an authenticated, user-local OpenAI Codex CLI installation as a Visual Agent provider. The Codex implementation must be consumed as a published JAR and must not vendor or reimplement the Codex protocol in this repository.

## Preconditions

- The user has installed and authenticated the official Codex CLI.
- The configured executable is an absolute executable path, or the CLI is discoverable on `PATH`/the user-local bin directory.
- The provider profile uses adapter `CODEX_CLI`.

## Main Flow

1. The provider locator validates the configured executable with `codex --version`.
2. The provider creates `ExecuteOptions` values from the Visual Agent request.
3. The published Spring AI Codex Agent JAR executes the CLI through its `CodexClient` API.
4. The final `agent_message` JSON event is mapped to the Spring AI `ChatResponse` contract.
5. A streaming request emits the complete result as one terminal item because the published Agent API is synchronous and does not expose token streaming.

## Prompt Mapping

The provider preserves the full Spring AI message sequence in the agent goal. Each message is prefixed with its role (`system`, `user`, or `assistant`) and separated by a blank line. No API key or provider credential is inserted into the goal.

## Configuration

The provider profile supports:

- `codex.executable.path`: optional absolute path to the Codex executable.
- `defaultModel`: optional model identifier. When blank, the published library and CLI choose their configured default; no synthetic model is added to the selector.

The published library does not enumerate account models. After startup and on manual refresh, Visual Agent invokes the official `codex debug models` command and reads its machine-readable catalog. Only each model's identifier and display name are retained; the raw catalog is never logged or persisted. The returned catalog replaces stale model selections without inventing model identifiers.

The JAR applies workspace-write sandboxing, smart approval policy, a five-minute operation timeout, and skips the Git repository check. Model refresh starts a short-lived official CLI catalog command; Visual Agent does not maintain an app-server protocol client.

## Library Decision

The implementation uses the published Apache-2.0 artifact `org.springaicommunity.agents:agent-codex:0.16.0`, which brings the matching `agent-model` and `codex-cli-sdk` artifacts transitively. The dependency is declared in `gradle/libs.versions.toml` and consumed by `:providers` only. The repository contains no third-party source tree, included build, or copied Codex protocol implementation.

The published API exposes `CodexClient` execution, not the lower-level Codex app-server protocol. Consequently, Visual Agent does not claim token-level streaming, dynamic Visual Agent tool callbacks, or app-server approval dialogs for this provider. These capabilities require a future library API that supports them; they must not be recreated locally as a compatibility layer.

## Tool Calls

- None. Codex execution is performed by the published Agent JAR; Visual Agent does not expose a custom Codex protocol tool path.

## Code Entry Points

- `modules/providers/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliLocator.kt`
- `modules/providers/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliProcessFactory.kt`
- `modules/providers/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexAgentBridge.kt`
- `modules/providers/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliProvider.kt`
- `modules/providers/src/main/kotlin/de/heckenmann/visualagent/agent/codex/CodexCliModelCatalog.kt`

## Error Handling

- Missing CLI: report that Codex is not installed.
- Invalid explicit path: report that the configured path is invalid and do not silently fall back to another executable.
- Non-zero agent execution: return a generic provider failure and do not expose raw SDK output or credentials in the model context.
- Cancellation: check the request cancellation token before starting the library call. The published synchronous API owns process cancellation and timeout behavior.

## Security

- The executable path is validated before use.
- Working-directory selection is normalized and passed as the request working directory.
- API keys are never copied into prompts, tool output, logs, or exported configuration.
- No repository-local third-party source is compiled or executed.

## Verification

- Unit tests cover model-catalog parsing, provider catalog persistence, response-event extraction, and bounded CLI process execution.
- The optional real-CLI smoke test verifies an end-to-end assistant response through the production bridge. It remains outside the default suite because it requires a locally authenticated Codex account; enable it with `-Dvisualagent.codex.smoke=true`, `-Dvisualagent.codex.smoke.executable=...`, and `-Dvisualagent.codex.smoke.model=...`.

## References

- [Spring AI Community Agent Client](https://springaicommunity.mintlify.app/projects/incubating/agent-client)
- [Codex Agent reference](https://springaicommunity.mintlify.app/agent-client/reference/codex-reference)
