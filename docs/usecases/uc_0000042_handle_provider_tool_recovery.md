# UC-0000042: Handle Provider Tool Recovery

## Goal

Recover gracefully when a provider emits unknown or malformed tool-call names.

## Primary Actor

LLM provider.

## Preconditions

- Tool callbacks are enabled for the request.
- A provider emits a tool call that cannot be mapped directly.

## Main Flow

1. The provider adapter receives the unknown tool-call name.
2. The recovery logic compares it with available function names.
3. A structured fallback or corrective response is produced.
4. The model can continue with valid tool names.

## Result

Tool-calling failures are explainable and recoverable instead of silently failing.

## Tool Calls

- Malformed or unknown provider tool-call names are recovered by `ToolRegistry`; no user-invoked tool is required.

## Code Entry Points

- `de.heckenmann.visualagent.agent.OllamaToolRecovery`
- `de.heckenmann.visualagent.agent.tools.ToolRegistry`
- `de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory`
- `de.heckenmann.visualagent.agent.OllamaPromptFactory`

## Acceptance Criteria

- Unknown tool names do not crash the application.
- Recovery output lists valid function names for the active request.
