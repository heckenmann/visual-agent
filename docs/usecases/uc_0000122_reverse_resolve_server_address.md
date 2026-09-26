# UC-0000122: Reverse Resolve a Server Address

## Goal

Let an enabled model perform a reverse DNS PTR lookup for one numeric IPv4 or IPv6 address from the
Visual Agent server.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `network:reverse-dns` tool is enabled for the requesting agent.
- DNS diagnostic access is allowed by the global tool policy.

## Main Flow

1. The model supplies one numeric IPv4 or IPv6 address.
2. Optionally, it supplies a numeric DNS server address and port (default 53).
3. The server validates both addresses and the port.
4. The server requests the corresponding PTR record from the system resolver or selected DNS server.
5. The tool returns unique, normalized, sorted, bounded PTR names.

## Result

The model receives reverse DNS information without shell execution or a forward lookup action mixed
into this tool.

## Tool Calls

- `network:reverse-dns`: query PTR for one IP, for example
  `{"address":"192.0.2.53","dnsServer":"192.0.2.1","dnsPort":53}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.NetworkReverseDnsTool`
- `de.heckenmann.visualagent.agent.tools.HostResolver`
- `de.heckenmann.visualagent.agent.tools.JvmHostResolver`

## Acceptance Criteria

- Only numeric IPv4/IPv6 addresses are accepted; hostnames and URLs are rejected.
- System DNS is the default; the user may select a DNS server and port.
- PTR results are normalized, deduplicated, sorted, and capped.
- Tests use a fake resolver and require no public DNS or Internet access.
