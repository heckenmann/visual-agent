# UC-0000144: Inspect Connector Diagnostic Availability

## Goal

Let the main agent determine whether Visual Agent's connector diagnostics are available without probing connectors, changing their state, or exposing credentials.

## Actors

- Main agent with `diagnostics:connectors` enabled.
- Visual Agent server.

## Preconditions

- The main agent is allowed to inspect diagnostic availability.

## Main Flow

1. The model calls `diagnostics_connectors` with an empty object.
2. The server reports whether a connector diagnostics registry is available and, if so, a safe configured-instance count.
3. When issue #52's connector framework is absent, the server returns `not_available` with the reason `connector_framework_not_configured`.

## Alternative Flows

- Invalid input is rejected; this diagnostic has no connector test or mutation action.
- Runtime failures return a normalized unavailable tool error.

## Result

The main agent does not speculate about connectors or try to bypass their security model through generic network or terminal tools.

## Tool Calls

- `diagnostics_connectors({})`: reports diagnostic availability only and never returns credentials.

## Code Entry Points

- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/DiagnosticsConnectorsTool.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/agent/tools/ServerConnectorDiagnosticsPortAdapter.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/api/ServerConnectorDiagnosticsPort.kt`
