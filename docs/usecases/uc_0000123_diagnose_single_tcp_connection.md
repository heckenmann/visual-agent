# UC-0000123: Diagnose a Single TCP Connection

## Goal

Let an enabled agent check whether the Visual Agent server can establish a TCP connection to one
explicit host and port.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `network:tcp` tool is enabled for the requesting agent.
- Network diagnostic access is allowed by the global tool policy.

## Main Flow

1. The agent supplies one hostname or IP literal, one port, and optionally an address family.
2. The server validates the host, port, and family without accepting URLs or path syntax.
3. The server resolves the host and attempts a bounded TCP connection to at most eight resolved
   addresses, with a five-second overall connection-attempt limit.
4. The server closes any established socket and returns the attempted addresses, connection status,
   elapsed time, and a normalized failure category when the connection could not be established.

## Result

The agent can distinguish DNS failure, timeout, connection refusal, unreachable network, and other
connection failures for one explicit endpoint. The tool does not scan ports or discover services.

## Tool Calls

- `network:tcp`: check one target, for example
  `{"host":"example.org","port":443,"family":"auto"}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.NetworkTcpTool`
- `de.heckenmann.visualagent.agent.tools.TcpConnectionProbe`
- `de.heckenmann.visualagent.agent.tools.JvmTcpConnectionProbe`

## Acceptance Criteria

- Hostnames, IPv4 literals, and IPv6 literals are supported; URLs and paths are rejected.
- Exactly one port from 1 through 65535 is accepted; port ranges are not supported.
- Address-family selection can restrict attempts to IPv4 or IPv6.
- Candidate count and connection duration are bounded.
- Error categories are normalized and never include raw exception messages.
- Tests use a fake probe and require no network access or timing-based assertions.
