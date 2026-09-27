# UC-0000125: Trace the Server Network Route

## Goal

Let an enabled agent inspect a bounded route trace from the Visual Agent server to one host.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `network:traceroute` tool is enabled for the requesting agent.
- The Visual Agent server runs Linux, macOS, or Windows.
- A platform traceroute utility is installed and available on `PATH`.

## Main Flow

1. The agent supplies one hostname or IP literal and optionally selects an address family, maximum
   hop count, and total timeout.
2. The server validates the host and enforces a maximum of 30 hops and 60 seconds.
3. JVM DNS resolution chooses a deterministic numeric destination in the requested family.
4. The server builds a fixed argument vector for Linux/macOS `traceroute` or Windows `tracert` and
   launches it without a shell. Output is capped and the process tree is terminated at the timeout.
5. Recognized hop rows are normalized to hop number, address, response status, and latency samples.

## Result

The agent receives a bounded route summary. Routers may filter or rate-limit probes; a hop with no
response does not prove that the router or link is down. The JVM has no standard API for retrieving
the sequence of IP hops, so this tool uses the operating system's dedicated route diagnostic
utility rather than adding a library. Platform options follow the
[Linux traceroute manual](https://man7.org/linux/man-pages/man8/traceroute.8.html),
[macOS traceroute manual](https://man.freebsd.org/cgi/man.cgi?apropos=0&manpath=macOS+10.13.6&query=traceroute&sektion=8),
and [Microsoft tracert documentation](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/tracert).

## Tool Calls

- `network:traceroute`: trace one destination, for example
  `{"host":"example.org","family":"auto","maxHops":20,"timeoutSeconds":30}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.NetworkTracerouteTool`
- `de.heckenmann.visualagent.agent.tools.JvmTracerouteProbe`
- `de.heckenmann.visualagent.agent.tools.TracerouteCommandBuilder`
- `de.heckenmann.visualagent.agent.tools.TracerouteOutputParser`
- `de.heckenmann.visualagent.agent.tools.JvmDiagnosticProcessRunner`

## Acceptance Criteria

- Hostnames and IP literals are supported; URLs and paths are rejected.
- Address family, hop count, timeout, and captured output are bounded.
- Linux/macOS and Windows use fixed platform arguments, with no shell invocation.
- Missing executables, cancellation, timeouts, and unrecognized/no-hop output return normalized statuses,
  retaining bounded partial results when available.
- Tests use fake resolvers/process runners and fixture output; they make no live network calls and do
  not rely on elapsed-time thresholds.
