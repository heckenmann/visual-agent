# UC-0000059: Query Runtime Context Tool

## Goal

Let enabled agents query the current runtime context, including workspace, provider, model, theme, and request metadata.

## Primary Actor

Enabled agent.

## Preconditions

- The context tool is enabled.

## Main Flow

1. The model calls the context tool.
2. The tool reads workspace and active provider/model settings.
3. Request metadata entries are appended.
4. Sensitive keys are summarized as configured/not configured.

## Result

Agents can orient themselves without receiving unrestricted global state automatically.

## Tool Calls

- `context`: returns request-safe runtime context.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.ContextTool`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`

## Acceptance Criteria

- Raw API keys are never returned.
- Request metadata is included in deterministic key order.
- The current workspace path is included.
