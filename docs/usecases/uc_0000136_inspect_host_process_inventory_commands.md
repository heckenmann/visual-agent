# UC-0000136: Inspect Host Process Inventory and Commands

## Goal

Let an explicitly enabled agent inspect process IDs and complete operating-system-reported command
lines for processes visible to the Visual Agent server account.

## Primary Actor

The main agent, or an explicitly configured sub-agent, trusted with all process command data visible
to the server account.

## Preconditions

- The `system:processes` tool has been enabled in the global tool settings and exposed to the
  requesting agent; it is globally disabled by default and absent from default sub-agent profiles.
- The host operating system provides the JDK `ProcessHandle` API.

## Main Flow

1. The agent requests a `list` page, an explicit `filter` query, or a `show` request for one
   non-negative process ID. List and filter use a zero-based offset and a page size from 1 to 100.
2. The server enumerates process IDs visible to its OS account and sorts them by PID. List and show
   read command details only for the selected page or PID; filter reads commands to match the query.
3. The requested page is returned with the snapshot's total count and `hasMore` indicator, or the
   exact requested process is returned. A filter matches command line, executable, or arguments
   case-insensitively and returns the original unmodified values.
4. The server includes the full command line and argument vector exactly as exposed by the operating
   system; unavailable values are omitted.

## Result

The default listing does not filter, truncate, or redact process IDs, commands, or arguments. Command arguments
can contain API keys, passwords, or other secrets. Enable this tool only for an agent trusted with
all process command data visible to the server account. Pagination bounds each response, but the
inventory can change between page requests. The tool does not inspect other processes' memory or
environment and cannot mutate or signal processes. This is the server host inventory; use
`system:client-processes` for the distinct desktop-client host.

## Tool Calls

- `system:processes`: first page: `{"action":"list","offset":0,"pageSize":50}`.
- `system:processes`: search commands: `{"action":"filter","query":"java","offset":0,"pageSize":50}`.
- `system:processes`: inspect one exact PID: `{"action":"show","pid":1234}`.
- Continue listing by increasing `offset` by the requested `pageSize` until `hasMore` is false.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.SystemProcessesTool`
- `de.heckenmann.visualagent.agent.tools.JvmHostProcessInventoryProbe`
- `de.heckenmann.visualagent.agent.tools.HostProcessInventoryProbe`

## Acceptance Criteria

- Returns every process visible to the current server account across successive pages.
- Filter matches command line, executable, and arguments case-insensitively while preserving the
  original returned values and bounded pagination.
- Includes the exact command line, executable, and argument vector returned by the JDK without
  filtering, truncation, or redaction.
- Does not inspect process environment or memory and provides no process mutation operation.
- Is absent from default profiles and can be explicitly enabled for a selected agent.
- Tests verify pagination, command filtering, exact command preservation, unavailable-command
  behavior, and invalid request bounds without depending on a specific host process inventory.
