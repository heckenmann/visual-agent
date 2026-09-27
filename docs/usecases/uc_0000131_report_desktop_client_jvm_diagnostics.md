# UC-0000131: Report Desktop Client JVM Diagnostics

## Goal

Allow the main agent to inspect safe runtime metrics from the desktop client's JVM independently
from diagnostics collected by the Visual Agent server.

## Primary Actor

The main agent, when explicitly asked to diagnose the desktop client.

## Preconditions

- The `system:client-runtime` tool is enabled.
- A request-scoped client-data requester is available to the server.

## Main Flow

1. The desktop sends an ordinary chat request without collecting or attaching a client snapshot.
2. Only when the main agent calls `system:client-runtime` does the server invoke the request-scoped
   client-data requester to collect the current JVM and operating-system metrics.
3. The tool labels the returned data as `desktop-client-jvm`, distinct from server-scoped diagnostics.

## Result

Client metrics and server metrics remain explicitly distinct. Server-side `context`,
`system:threads`, `system:gc`, `system:process`, `system:processes`, and `system:filesystem` describe
the server JVM/host. This tool reports only data collected after its explicit tool invocation.
`system:client-processes` separately requests the distinct client-host process inventory. In
embedded mode both scopes may describe the same JVM; in remote mode they describe distinct JVMs.
Missing client diagnostics do not block conversation requests.
The request-scoped client-data channel itself is not persisted. Once explicitly called, the tool
result follows normal tool-call history persistence and may be available in later conversation
history; do not enable the tool for data that must never be retained.

## Tool Calls

- `system:client-runtime`: request the client's current JVM snapshot, for example `{}`.

## Code Entry Points

- `de.heckenmann.visualagent.desktop.JvmClientRuntimeDiagnosticsPort`
- `de.heckenmann.visualagent.ui.application.ClientRuntimeConversationPort`
- `de.heckenmann.visualagent.protocol.ClientDataRequestPort`
- `de.heckenmann.visualagent.server.VisualAgentGrpcSessionService`
- `de.heckenmann.visualagent.agent.tools.SystemClientRuntimeTool`

## Acceptance Criteria

- An ordinary chat does not capture or transmit client metrics.
- The client metrics provider is invoked only after the server executes this tool.
- The general context tool and ordinary chats do not reveal a client snapshot before the explicit
  call; the completed tool result can be retained in conversation history.
- Missing or failed client snapshot collection does not prevent chat submission.
- Tests verify lazy collection, request-scoped access, and tool response without timing-based assertions or external
  services.
