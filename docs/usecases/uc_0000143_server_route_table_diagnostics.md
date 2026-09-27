# UC-0000143: Inspect Server Route Tables

## Goal

Let an enabled model inspect the Visual Agent server's IPv4 and IPv6 routing tables without running arbitrary commands or probing arbitrary hosts.

## Actors

- Researcher or analyst sub-agent with `network:routes` enabled.
- Visual Agent server host.

## Preconditions

- The server runs on Linux, macOS, or Windows.
- The agent is allowed to use network diagnostics.

## Main Flow

1. The model calls `network_routes` with `{}` or selects `all`, `ipv4`, or `ipv6`.
2. The server runs fixed per-platform route-table commands with a minimal child environment, a five-second timeout per table, and a 12,000-character capture limit.
3. The tool returns normalized route records with destination, gateway, interface, default-route flag, and metric when the operating system reports it, alongside bounded native output and execution status for each address family.

## Alternative Flows

- Unsupported operating systems return `unsupported_platform` without launching a process.
- Missing utilities, timeouts, cancellation, or nonzero exit codes return normalized status without arbitrary process output.
- Output at the capture limit is marked truncated.

## Result

The model can inspect the current server routing configuration while command execution remains fixed, bounded, and shell-free.

## Tool Calls

- `network_routes({"family":"all"})`: returns normalized IPv4 and IPv6 routes, route-table statuses, and bounded native text.
- `network_routes({"family":"ipv4"})`: returns only the IPv4 route table.
- `network_routes({"family":"ipv6"})`: returns only the IPv6 route table.

## Code Entry Points

- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/NetworkRoutesTool.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/RouteTablePlatformSupport.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/DiagnosticProcessRunner.kt`
