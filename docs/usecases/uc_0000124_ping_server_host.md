# UC-0000124: Check Server-Side Host Reachability

## Goal

Let an enabled agent perform bounded, best-effort reachability checks from the Visual Agent server
to one host and inspect response and elapsed-time summaries.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `network:ping` tool is enabled for the requesting agent.
- The Visual Agent server can resolve and attempt to reach the target.

## Main Flow

1. The agent supplies one hostname or IP literal and optionally selects `auto`, `ipv4`, or `ipv6`.
2. It may select a packet count from 1 to 10 and an overall timeout from 1 to 30 seconds.
3. The server validates the target and all bounds.
4. The server resolves the target through the JVM resolver and selects a deterministic address in
   the requested family.
5. The server calls `InetAddress.isReachable` for each attempt, passing the remaining overall
   timeout. The JVM may use ICMP Echo when available or a platform-specific fallback such as TCP
   Echo; the API does not guarantee raw ICMP behavior or packet-level statistics.
6. The tool returns the number of completed checks, checks that reported reachability, an
   unanswered-attempt percentage, and response-time summaries for successful checks.

## Result

The result is a best-effort JVM reachability diagnostic, not a guaranteed ICMP ping. A check that
does not report reachability is not proof that the target is down because firewalls, privileges, or
network policies may affect the JVM's implementation strategy.

## Tool Calls

- `network:ping`: ping one target, for example
  `{"host":"example.org","family":"auto","count":4,"timeoutSeconds":5}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.NetworkPingTool`
- `de.heckenmann.visualagent.agent.tools.PingProbe`
- `de.heckenmann.visualagent.agent.tools.JvmPingProbe`
- `de.heckenmann.visualagent.agent.tools.JvmReachabilityChecker`

## Implementation Decision

The JDK already exposes `InetAddress.isReachable(timeout)`, so ping-like reachability checks use
that API and add no dependency or native process. Per the
[Java 24 `InetAddress` documentation](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/net/InetAddress.html),
the implementation may try ICMP Echo when possible and otherwise may use a TCP Echo fallback;
results are therefore best-effort and are not equivalent to guaranteed ICMP packet statistics.

## Acceptance Criteria

- Hostnames and IPv4/IPv6 literals are accepted; URLs and path syntax are rejected.
- Attempt count and overall requested timeout are bounded.
- Address family selection is deterministic.
- The result does not overstate a failed reachability check as proof that a host is down.
- Tests use fake resolvers, reachability checks, and monotonic clocks; they make no live network calls and contain no timing-based assertions.
