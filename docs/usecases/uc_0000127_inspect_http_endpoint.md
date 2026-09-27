# UC-0000127: Inspect an HTTP Endpoint

## Goal

Let an enabled agent check HTTP(S) reachability and response metadata from the Visual Agent server.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `network:http` tool is enabled for the requesting agent.
- The endpoint accepts HTTP or HTTPS requests from the server.

## Main Flow

1. The agent supplies one HTTP(S) URL, optionally selecting GET or HEAD and a timeout.
2. The server rejects unsupported schemes, embedded URL credentials, fragments, invalid ports, and
   invalid timeout values.
3. The JDK HTTP client sends the request without caller-supplied headers and never exposes a response
   body.
4. Redirects are followed manually up to five hops; malformed redirects and HTTPS-to-HTTP
   downgrades are rejected. Redirect results contain only sanitized scheme/host/port authorities.
5. The tool returns normalized status, elapsed time, response status, content type, content length,
   and the bounded redirect chain.

## Result

The result never includes request headers, response headers generally, response body content, URL
paths, queries, fragments, or user-info. HTTP checks are server-side and intentionally can target
private/internal hosts for network diagnosis; they do not carry app credentials or cookies.

## Tool Calls

- `network:http`: inspect an endpoint, for example
  `{"url":"https://example.org/health","method":"GET","timeoutSeconds":10}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.NetworkHttpTool`
- `de.heckenmann.visualagent.agent.tools.JvmHttpDiagnosticProbe`
- `de.heckenmann.visualagent.agent.tools.JvmHttpDiagnosticTransport`
- `de.heckenmann.visualagent.agent.tools.HttpDiagnosticTransport`

## Acceptance Criteria

- Uses the JDK HTTP client; no extra HTTP library is added.
- Allows only GET/HEAD, HTTP(S), one URL, and a bounded timeout/redirect count.
- Never returns response bodies, arbitrary headers, URL query strings, fragments, or credentials.
- Rejects HTTPS-to-HTTP redirects and normalizes connection, timeout, cancellation, and redirect
  errors.
- Tests use fake transport responses; they make no public-network calls or timing-based assertions.
