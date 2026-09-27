# UC-0000139: Inspect Effective Server Configuration

## Goal

Allow an authorized agent to inspect the active Visual Agent server provider/model selection and selected runtime limits without revealing credentials, user instructions, or local filesystem paths.

## Preconditions

- The `diagnostics:config` tool is enabled for the agent.
- The server configuration and provider catalog are available.

## Main Flow

1. The model calls `diagnostics_config` with an empty JSON object.
2. The server reads the active selection from the provider catalog and safe runtime values from the application configuration.
3. The server validates that the provider and model exist and that runtime limits are positive.
4. The tool returns the provider adapter, sanitized endpoint origin, active model, declared model limits/capabilities, context window, timeout, parallel-agent limit, and validation warnings.

## Security and Limits

- API keys and other credential values are never returned; only whether a key is configured is reported.
- Endpoint credentials, path, query, and fragment are omitted.
- Filesystem/database paths, user model instructions, and system properties are not included.
- If the configuration source fails, the tool returns a normalized error without exception details.

## Tool Calls

- `diagnostics:config`: `{"action":"get"}` or `{}`. No network call is made.

## Code Entry Points

- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/DiagnosticsConfigTool.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/agent/tools/ServerConfigurationDiagnosticsPortAdapter.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/api/ServerConfigurationDiagnosticsPort.kt`
