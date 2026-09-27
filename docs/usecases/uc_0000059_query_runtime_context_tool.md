# UC-0000059: Query Runtime Context Tool

## Goal

Let enabled agents query the current runtime context, including workspace, provider, model, theme, and request metadata.

## Primary Actor

Main agent or explicitly enabled sub-agent.

## Preconditions

- The context tool is enabled for the requesting agent.

## Main Flow

1. The model calls the context tool.
2. The tool reads workspace and active provider/model settings.
3. Request metadata entries are appended.
4. Sensitive keys are summarized as configured/not configured.

## Result

Agents can orient themselves without receiving unrestricted global state automatically. The response
also includes bounded OS/JVM details, JVM memory values, and optional physical-memory/CPU metrics.

## Tool Calls

- `context`: returns request-safe runtime context.
- Runtime details include OS name/version/architecture, processor count, Java/JVM identity and uptime,
  heap/non-heap usage, and physical-memory/CPU metrics only when the platform exposes them.
- Missing optional metrics are omitted; environment variables, full system-property dumps, command
  lines, usernames, home directories, and API-key values are not included by runtime diagnostics.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.ContextTool`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`

## Acceptance Criteria

- Raw API keys are never returned.
- Only allowlisted request metadata is included in deterministic key order; cancellation objects
  and unknown metadata keys are omitted.
- Endpoint user-info and query/fragment parameters are removed before a provider base URL is shown.
- The current workspace path is included.
- Runtime metric collection failure does not fail the context request.
