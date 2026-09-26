# UC-0000128: Inspect a TLS Endpoint

## Goal

Let an enabled agent inspect TLS connectivity, certificate trust, hostname verification, and
negotiated protocol for one server-side endpoint.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `network:tls` tool is enabled for the requesting agent.
- The server can resolve and reach the selected host and port.

## Main Flow

1. The agent supplies one hostname or IP address, a port, an optional address family, and a bounded
   timeout.
2. The server validates the host and input bounds; DNS resolution uses the JVM system resolver.
3. JSSE performs a TLS handshake using the JVM default trust store. SNI is sent for DNS hostnames,
   and the JVM HTTPS hostname verifier checks the peer identity before the result is accepted.
4. The tool returns a normalized status, selected numeric address, negotiated protocol and cipher
   suite when available, verification results, and bounded public metadata from the peer certificate.

## Result

The result contains no private key material, request data, raw exceptions, or local trust-store
contents. DNS resolution uses the system resolver; this tool does not accept a custom DNS server.
DNS lookup remains available through the independent `network:dns` tool, and reverse lookup through
the independent `network:reverse-dns` tool.

## Tool Calls

- `network:tls`: inspect one endpoint, for example
  `{"host":"example.org","port":443,"family":"auto","timeoutSeconds":10}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.NetworkTlsTool`
- `de.heckenmann.visualagent.agent.tools.JvmTlsDiagnosticProbe`
- `de.heckenmann.visualagent.agent.tools.JvmTlsHandshakeProbe`
- `de.heckenmann.visualagent.agent.tools.TlsDiagnosticProbe`

## Acceptance Criteria

- Uses JDK JSSE and the default JVM trust configuration; no extra TLS library is added.
- Verifies the certificate chain and hostname independently, with no trust bypass or hostname
  heuristic.
- Bounds input, address candidates, socket timeout, and returned certificate metadata.
- Tests use fake resolvers and handshake probes; they make no public-network calls or timing-based
  assertions.
