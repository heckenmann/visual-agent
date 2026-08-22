# UC-0000104: Execute sandboxed JavaScript with agent tools

## Goal

Allow an enabled agent to run a JavaScript program for deterministic multi-tool orchestration and local data processing without exposing host capabilities or sending intermediate results through another model turn.

## Actors

- Main agent or an explicitly configured sub-agent.
- Spring application server owning the runtime and tool registry.
- GraalJS guest runtime created for one execution.

## Preconditions

- The current request explicitly enables `javascript:execute`.
- The request-scoped tool set contains the tools the script is expected to call.
- The server is running with the configured JavaScript resource limits.

## Flow

1. The agent calls `javascript:execute` with inline `source` or a workspace-relative JavaScript `path`.
2. The server creates a fresh GraalJS context with host access, IO, process, network, and polyglot access disabled. Script source length is not capped.
3. The context receives only `tools.call(name, arguments)`, `tools.list()`, `tools.describe(name)`, the hardened `workspace.write/read/delete(...)` helpers, and bounded simulated `console` methods.
4. Each `tools.call` verifies the request-scoped allowlist and invokes the existing `ToolRegistry` path. Nested activity keeps normal lifecycle events, cancellation, authorization, and timeout behavior.
5. The script filters, aggregates, transforms, or combines results locally. For complex deterministic logic or large generated text (for example CSV exports and Markdown tables), it assembles the complete output before returning it. It can return a string, Markdown document, primitive, array, object, or null.
6. Only the final `return` value becomes the model-visible tool result. Console diagnostics remain bounded execution metadata and never use the server's real stdout/stderr.
7. If execution fails, the tool returns a compact category and message to the model. The model can correct the source or arguments and retry; an unchanged failing script must not be repeated.
8. The server enforces time, result, call-count, concurrency, workspace-write, and recursion limits, then closes the context. Script source length is not capped; the normal tool-call timeout remains the execution bound.

## Tool Calls

- `javascript:execute` with `{ "source": "...", "timeoutSeconds": 120 }` or `{ "path": "scripts/report.js" }`; `timeoutSeconds` is optional and uses the application default when omitted.
- Script-internal `tools.call("<enabled canonical tool id>", { ... })` calls through the existing registry.
- Script-internal `tools.list()` and `tools.describe(name)` for request-scoped discovery.
- Script-internal `workspace.write({path: "relative/file.md", content: text})`, `workspace.read({path: "relative/file.md"})`, and `workspace.delete({path: "relative/file.md"})` for hardened UTF-8 workspace file access.

## Output

The script's return value is the authoritative output. Markdown is returned as an ordinary string and must not be normalized before the conversation renderer receives it.

```javascript
const workspace = await tools.call("workspace:file", { action: "list" });
return ["# Files", "", ...workspace.files.map(file => `- ${file.name}`)].join("\n");
```

`console.log`, `console.info`, `console.warn`, and `console.error` are simulated, bounded diagnostics. They are not a second model result channel, are not written to process logs, and cannot access host output streams.

Execution exceptions are returned to the model as actionable tool errors (for example `SYNTAX`, `RUNTIME`, `TOOL_ARGUMENTS`, `TIMEOUT`, or `LIMIT_EXCEEDED`) without stack traces or internal paths.

## Security and limits

- No JVM classes, reflection, host objects, filesystem, environment variables, process creation, native APIs, network, credentials, or unrestricted application services are exposed.
- A script may call only tools enabled in the current request; `javascript:execute` cannot call itself recursively.
- Parent cancellation cancels the guest context and in-flight bridge calls.
- Workspace writes count toward the request tool-call budget and are bounded per file and cumulatively for the execution. These output limits do not cap JavaScript source length.
- Syntax, runtime, access, tool, timeout, cancellation, and limit failures are returned as compact safe categories without stack traces or internal paths.

## Code Entry Points

- `modules/tool-javascript/.../agent/javascript/GraalJavaScriptExecutionService.kt`
- `modules/tool-javascript/.../agent/javascript/JavaScriptToolBridge.kt`
- `modules/tool-javascript/.../agent/javascript/JavaScriptExecuteTool.kt`
- `application/.../agent/context/MainSystemPromptComposer.kt`
- `application/.../agent/AgentToolConfigService.kt`

## Verification

- `GraalJavaScriptExecutionServiceTest` covers values, Markdown, console diagnostics, async tool calls, local transformations, access denial, sandbox boundaries, and parent cancellation.
- The complete project quality gate must pass before release.
