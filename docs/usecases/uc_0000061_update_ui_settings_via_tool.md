# UC-0000061: Update UI Settings Via Tool

## Goal

Let enabled agents inspect or update safe UI/session settings through explicit tool calls.

## Primary Actor

Enabled agent.

## Preconditions

- The UI tool is enabled.
- The requested setting is supported by the tool.

## Main Flow

1. The model calls the UI tool with `get` or `set`.
2. The tool reads or updates supported settings.
3. Updates are persisted through application configuration.
4. The tool returns a sanitized settings summary.

## Result

Agents can adjust safe runtime preferences without accessing raw credentials.

## Tool Calls

- `ui` actions: `get`, `set` for supported safe UI/session settings.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.UiTool`
- `de.heckenmann.visualagent.config.AppConfig`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`

## Acceptance Criteria

- Raw API keys are never returned.
- Provider Base URLs and model identifiers may be returned, but credentials are omitted.
- Font size is clamped to the supported range.
- Unsupported actions return a clear failure.
