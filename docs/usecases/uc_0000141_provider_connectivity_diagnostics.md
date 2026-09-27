# UC-0000141: Check Provider Connectivity

## Goal

Let the main agent determine whether the active Visual Agent provider responds to a model-catalog request, without exposing credentials or changing the persisted provider configuration.

## Actors

- Main agent with `diagnostics:provider` enabled.
- Active configured provider endpoint or local provider runtime.

## Preconditions

- A provider profile is selected.
- The main agent is allowed to use the provider diagnostics tool.

## Main Flow

1. The model calls `diagnostics_provider` with an empty object.
2. The server performs a read-only model-catalog request against the active provider profile.
3. The server reports provider ID, adapter, sanitized endpoint origin, whether credentials are configured, discovered model count, and whether the selected model was returned. For a returned selected model, it includes provider-reported capabilities and whether that list is complete.
4. Provider failures are mapped to a fixed, safe category; raw response bodies, credentials, and endpoint paths are omitted.
5. The diagnostic request does not update the persisted model catalog.

## Alternative Flows

- If the active profile is missing or disabled, the tool reports a normalized unavailable state without contacting a provider.
- If discovery fails, the tool reports a sanitized provider error category and omits model count and availability; it does not interpret an API failure as an absent model.
- If the selected model is not in the returned list, the tool reports that the connection responded but the selected model is unavailable.

## Result

The main agent can distinguish provider connectivity from model selection problems without invoking unrestricted network tools or exposing credentials.

## Tool Calls

- `diagnostics_provider({})`: performs an active read-only provider model-catalog request and returns sanitized connectivity and selected-model status.

## Code Entry Points

- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/DiagnosticsProviderTool.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/agent/tools/ServerProviderDiagnosticsPortAdapter.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/api/ServerProviderDiagnosticsPort.kt`
