# UC-0000121: Resolve Server Host Addresses

## Goal

Let an enabled model inspect IPv4 and IPv6 DNS answers as seen from the Visual Agent server, using
the operating-system resolver or an explicitly selected DNS server.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `network:dns` tool is enabled for the requesting agent.
- DNS diagnostic access is allowed by the global tool policy.

## Main Flow

1. The model provides one hostname or IP address and selects `ipv4`, `ipv6`, or `all`.
2. Optionally, the model supplies a numeric IPv4/IPv6 `dnsServer` and `dnsPort` (default 53).
3. The server validates the hostname, address family, DNS server address, and port.
4. Resolution runs inside the common bounded/cancellable tool execution path.
5. The tool returns deduplicated, sorted, bounded A/AAAA answers and identifies whether the system
   or explicitly selected DNS server was used.

## Result

The model receives normalized resolver results without shell execution, arbitrary record types,
port scanning, or exposing resolver configuration. Private/internal targets remain valid diagnostic
targets.

## Tool Calls

- `network:dns`: resolve one hostname; for example
  `{"host":"example.org","family":"all","dnsServer":"192.0.2.53","dnsPort":53}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.NetworkDnsTool`
- `de.heckenmann.visualagent.agent.tools.HostResolver`
- `de.heckenmann.visualagent.agent.tools.JvmHostResolver`

## Implementation Decision

The JVM `InetAddress` resolver remains the default to preserve system resolver behavior. For an
explicit server, use dnsjava rather than hand-rolling DNS packets: the official dnsjava project
implements DNS queries and its `SimpleResolver` supports an explicitly configured resolver address
([project](https://github.com/dnsjava/dnsjava), [resolver API](https://javadoc.io/doc/dnsjava/dnsjava/latest/org/xbill/DNS/SimpleResolver.html)).
Version 3.6.5 is BSD-3-Clause licensed and is used only for A/AAAA questions in this first
increment ([release](https://github.com/dnsjava/dnsjava/releases/tag/v3.6.5-1)). No matching Kotlin-
specific DNS client was found in the Klibs directory; the Java protocol library is maintained and
fits this server-side JVM module.

## Acceptance Criteria

- Forward family filtering uses actual IPv4/IPv6 address types.
- Duplicate answers are removed and output is sorted and capped.
- URL syntax, arbitrary DNS server hostnames, invalid ports, and unsupported families are rejected.
- Tests use a fake resolver and require no public DNS or Internet access.
- DNS server selection is verified through the resolver contract.
