# UC-0000137: Inspect Desktop Client Process Inventory

## Goal

Let an explicitly authorized agent inspect complete process IDs and operating-system-reported
commands from the desktop client host, separately from the Visual Agent server host.

## Primary Actor

The main agent, when connected to a desktop client and explicitly trusted with its process
command data.

## Preconditions

- The `system:client-processes` tool has been enabled in global tool settings. It is globally
  disabled by default and cannot be assigned to a sub-agent because only the active main-agent
  conversation has a request-scoped client-data channel.
- A request-scoped client-data requester is available to the server.
- The conversation request is connected to the desktop client whose processes are being inspected.

## Main Flow

1. An ordinary conversation request contains no client process information and does not enumerate
   client processes.
2. The agent calls `system:client-processes` with a bounded `list` page or an exact PID `show`.
3. The server invokes the request-scoped client-data requester with the same action, offset, page
   size, or PID. The client returns only that bounded page or process; it does not transfer command
   lines for other processes.
4. The tool returns records with `hostRole=client`, preserving operating-system-reported values
   without filtering or redaction.

## Result

Client process data is distinct from `system:processes`, which inspects the server host. The tool
does not inspect process environments or memory and cannot mutate or signal processes. Commands and
arguments may contain credentials or other secrets; enable this tool only when the agent is trusted
with all process command data from the connected client. The inventory is neither collected nor
transferred unless the enabled tool is called. The completed tool result follows normal tool-call
history persistence and may retain commands or credentials in the server database.

## Tool Calls

- `system:client-processes`: first page: `{"action":"list","offset":0,"pageSize":50}`.
- `system:client-processes`: inspect one PID: `{"action":"show","pid":1234}`.
- Continue listing by increasing `offset` by `pageSize` until `hasMore` is false.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.SystemClientProcessesTool`
- `de.heckenmann.visualagent.desktop.JvmClientProcessInventoryDiagnosticsPort`
- `de.heckenmann.visualagent.protocol.ClientDataRequestPort`
- `de.heckenmann.visualagent.ui.application.ClientRuntimeConversationPort`

## Acceptance Criteria

- Results explicitly identify the client host and never mix with server process results.
- Every client process exposed by the OS snapshot can be retrieved through successive bounded pages.
- Command lines and argument vectors are returned exactly as supplied by the OS, without filtering,
  truncation, or redaction.
- Ordinary chats do not enumerate or transfer the client process inventory.
- The inventory is requested only when the server executes this tool and is absent from ordinary
  prompt text before the call; the completed tool result may be retained in conversation history.
- The tool is globally disabled by default and can be explicitly enabled for the main agent only.
- Tests cover lazy client collection, request metadata, host labeling, pagination, raw command
  preservation, and missing client data without relying on a specific process inventory.
