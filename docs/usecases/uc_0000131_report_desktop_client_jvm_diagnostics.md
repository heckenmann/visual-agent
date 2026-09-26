# UC-0000131: Report Desktop Client JVM Diagnostics

## Goal

Allow the main agent to inspect safe runtime metrics from the desktop client's JVM independently
from diagnostics collected by the Visual Agent server.

## Primary Actor

The main agent, when explicitly asked to diagnose the desktop client.

## Preconditions

- The `system:client-runtime` tool is enabled.
- The desktop client can provide a JVM snapshot for the current chat request.

## Main Flow

1. The desktop captures a bounded snapshot of its own JVM and operating system when submitting a
   chat request.
2. The snapshot is transferred as a separate protocol field; it is not added to conversation text,
   persisted history, or ordinary `context` output.
3. The main agent invokes `system:client-runtime` to retrieve the snapshot.
4. The tool labels the data as `desktop-client-jvm`, distinct from server-scoped diagnostics.

## Result

Client metrics and server metrics remain explicitly distinct. Server-side `context`,
`system:threads`, and `system:filesystem` describe the server JVM/host. This tool reports only the
separately supplied desktop client snapshot. In embedded mode both scopes may describe the same
JVM; in remote mode they describe distinct JVMs. Missing client diagnostics do not block
conversation requests.

## Tool Calls

- `system:client-runtime`: return the client snapshot attached to the current request, for example
  `{}`.

## Code Entry Points

- `de.heckenmann.visualagent.desktop.JvmClientRuntimeDiagnosticsPort`
- `de.heckenmann.visualagent.ui.application.ClientRuntimeConversationPort`
- `de.heckenmann.visualagent.server.VisualAgentGrpcSessionService`
- `de.heckenmann.visualagent.agent.tools.SystemClientRuntimeTool`

## Acceptance Criteria

- Client metrics are captured on the client side and passed independently from server diagnostics.
- The general context tool and conversation history do not reveal the client snapshot.
- Missing or failed client snapshot collection does not prevent chat submission.
- Tests verify the protocol transfer and tool response without timing-based assertions or external
  services.
